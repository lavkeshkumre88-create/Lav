const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const FREE_QUOTA_BYTES = 107374182400; // 100 GB

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, email = "alice@example.com") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Alice Cloud",
    email,
    photoUrl: "https://example.com/avatar.png",
    planTier: "FREE",
    quotaBytes: FREE_QUOTA_BYTES,
    usedBytes: 0,
    totalUploads: 0,
    totalDownloads: 0,
    role: "USER",
    accountStatus: "ACTIVE",
    autoBackupPhotos: true,
    autoBackupVideos: false,
    wifiOnlyBackup: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validFilePayload(uid, sizeBytes = 1024, kind = "PHOTO") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: kind === "FOLDER" ? "Vacation" : "sunset_vault.jpg",
    kind,
    mimeType: kind === "FOLDER" ? "inode/directory" : "image/jpeg",
    sizeBytes,
    parentFolderId: "",
    favorite: false,
    trashed: false,
    cloudSynced: true,
    chunkCount: kind === "FOLDER" ? 0 : 1,
    sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    localRelativePath: "vault/sunset_vault.jpg",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("files").get());
  await assertFails(unauthDb.collection("sharedLinks").get());
  await assertFails(unauthDb.collection("activityLogs").get());
});

test("User initialization enforces 100GB FREE quota and blocks self-upgrade or role escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  // Valid initial 100GB FREE profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  // Reject self-upgrading planTier or quotaBytes
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      planTier: "PRO_1TB",
      quotaBytes: 1099511627776,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject role escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject creating another user's profile or with boosted quota
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const tamperedBob = validUserPayload(BOB_UID, "bob@example.com");
  tamperedBob.planTier = "ULTRA_2TB";
  tamperedBob.quotaBytes = 2199023255552;
  await assertFails(bobDb.collection("users").doc(BOB_UID).set(tamperedBob));
});

test("Batched file upload updates usedBytes and enforces 100GB quota ceiling", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Batched file creation + user usedBytes increment succeeds within 100GB
  const batchOk = aliceDb.batch();
  const fileRef = aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1");
  const userRef = aliceDb.collection("users").doc(ALICE_UID);
  batchOk.set(fileRef, validFilePayload(ALICE_UID, 2048, "PHOTO"));
  batchOk.update(userRef, {
    usedBytes: 2048,
    totalUploads: 1,
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(batchOk.commit());

  // Quota overflow (usedBytes > quotaBytes) fails atomically
  const batchOverflow = aliceDb.batch();
  const fileOverflowRef = aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_2");
  batchOverflow.set(fileOverflowRef, validFilePayload(ALICE_UID, 1024, "DOCUMENT"));
  batchOverflow.update(userRef, {
    usedBytes: FREE_QUOTA_BYTES + 1,
    totalUploads: 2,
    updatedAt: new Date(Date.now() - 500),
  });
  await assertFails(batchOverflow.commit());

  // Cross-user isolation on files
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").delete());
});

test("Partial file updates preserve immutable fields and reject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("folder_1").set(
      validFilePayload(ALICE_UID, 0, "FOLDER")
    )
  );

  // Valid partial update (rename + favorite)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("folder_1").update({
      name: "Work Docs",
      favorite: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("folder_1").update({
      name: "Hacked",
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Shared links enforce owner queries, existence of target file, and safe access increment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_share_1").set(
      validFilePayload(ALICE_UID, 4096, "PDF")
    )
  );

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("sharedLinks").doc("link_abc").set({
      userId: ALICE_UID,
      fileId: "file_share_1",
      fileName: "sunset_vault.jpg",
      kind: "PDF",
      mimeType: "application/pdf",
      sizeBytes: 4096,
      passwordProtected: true,
      passwordHash: "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      expiresAtMillis: Date.now() + 86400000,
      revoked: false,
      accessCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner scoped list succeeds; unfiltered list fails
  await assertSucceeds(aliceDb.collection("sharedLinks").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("sharedLinks").get());

  // Recipient can get non-revoked link by exact ID and increment accessCount by 1
  await assertSucceeds(bobDb.collection("sharedLinks").doc("link_abc").get());
  await assertSucceeds(
    bobDb.collection("sharedLinks").doc("link_abc").update({
      accessCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Recipient cannot revoke Alice's link or skip accessCount
  await assertFails(
    bobDb.collection("sharedLinks").doc("link_abc").update({
      revoked: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner revokes link -> recipient can no longer read it
  await assertSucceeds(
    aliceDb.collection("sharedLinks").doc("link_abc").update({
      revoked: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(bobDb.collection("sharedLinks").doc("link_abc").get());
});

test("Admin RBAC blocks unverified email spoofing and permits verified admin management", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Spoofed admin email with email_verified: false MUST fail
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "lavkeshkumre88@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofedAdminDb.collection("users").get());

  // Verified bootstrapped admin succeeds in listing users and upgrading planTier
  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "lavkeshkumre88@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(verifiedAdminDb.collection("users").get());
  await assertSucceeds(
    verifiedAdminDb.collection("users").doc(ALICE_UID).update({
      planTier: "PRO_1TB",
      quotaBytes: 1099511627776,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Activity logs allow owner create/list and reject tampering or deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("activityLogs").doc("log_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      action: "UPLOAD",
      targetName: "sunset_vault.jpg",
      bytes: 2048,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("activityLogs").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("activityLogs").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("activityLogs").doc("log_1").update({ bytes: 999999 }));
  await assertFails(aliceDb.collection("activityLogs").doc("log_1").delete());
});

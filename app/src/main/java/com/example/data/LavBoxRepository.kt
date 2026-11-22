package com.example.data

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.model.ActivityLog
import com.example.model.FREE_QUOTA_BYTES
import com.example.model.FileChunk
import com.example.model.FileKind
import com.example.model.LogAction
import com.example.model.PRO_QUOTA_BYTES
import com.example.model.PlanTier
import com.example.model.SharedLink
import com.example.model.ULTRA_QUOTA_BYTES
import com.example.model.UserAccount
import com.example.model.VaultFile
import com.example.util.sha256String
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class LavBoxRepository(
    private val db: FirebaseFirestore,
    private val storageEngine: VaultStorageEngine,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        storageEngine = VaultStorageEngine(context.applicationContext)
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing LavBox cloud.")
        }
        return uid
    }

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeVaultFiles(userId: String): Flow<List<VaultFile>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val colRef = db.collection("users").document(uid).collection("files")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VaultFile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMySharedLinks(userId: String): Flow<List<SharedLink>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val query = db.collection("sharedLinks").whereEqualTo("userId", uid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedLink::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "sharedLinks")
                    }
                    throw error
                }
        )
    }

    fun observeMyActivityLogs(userId: String): Flow<List<ActivityLog>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val query = db.collection("activityLogs").whereEqualTo("userId", uid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivityLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "activityLogs")
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserAccount(firebaseUser: FirebaseUser): Result<Unit> {
        val path = "users/${firebaseUser.uid}"
        return try {
            val uid = requireUserId()
            check(uid == firebaseUser.uid) { "User mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanName = (firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: firebaseUser.email?.substringBefore("@")
                ?: "LavBox Member").take(100)
            val email = (firebaseUser.email ?: "").take(160)
            val photoUrl = firebaseUser.photoUrl?.toString()?.take(500)

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "planTier" to PlanTier.FREE.name,
                    "quotaBytes" to FREE_QUOTA_BYTES,
                    "usedBytes" to 0L,
                    "totalUploads" to 0L,
                    "totalDownloads" to 0L,
                    "role" to "USER",
                    "accountStatus" to "ACTIVE",
                    "autoBackupPhotos" to true,
                    "autoBackupVideos" to false,
                    "wifiOnlyBackup" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf(
                    "displayName" to cleanName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createFolder(
        userId: String,
        folderName: String,
        parentFolderId: String
    ): Result<Unit> {
        val folderId = "fld_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$userId/files/$folderId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val trimmed = folderName.trim()
            require(trimmed.length in 1..200) { "Folder name must be between 1 and 200 characters" }

            val payload = mapOf(
                "userId" to uid,
                "name" to trimmed,
                "kind" to FileKind.FOLDER.name,
                "mimeType" to "inode/directory",
                "sizeBytes" to 0L,
                "parentFolderId" to parentFolderId.take(128),
                "favorite" to false,
                "trashed" to false,
                "cloudSynced" to true,
                "chunkCount" to 0L,
                "sha256" to "",
                "localRelativePath" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("files").document(folderId)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun uploadFileFromUri(
        userId: String,
        userEmail: String,
        account: UserAccount,
        uri: Uri,
        fileName: String,
        mimeType: String,
        kind: FileKind,
        expectedBytes: Long,
        parentFolderId: String,
        isBackup: Boolean,
        isPaused: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): Result<VaultFile> {
        val fileId = "fil_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$userId/files/$fileId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeName = fileName.trim().ifBlank { "Untitled_File" }.take(200)
            val projectedRemaining = account.quotaBytes - account.usedBytes
            require(expectedBytes <= projectedRemaining) {
                "Storage quota exceeded. Upgrade plan or free up space in Trash."
            }

            val ingested = storageEngine.ingestFromUri(
                uid = uid,
                fileId = fileId,
                uri = uri,
                sanitizedFileName = safeName,
                expectedBytes = expectedBytes,
                isPaused = isPaused,
                onProgress = onProgress
            )

            require(account.usedBytes + ingested.sizeBytes <= account.quotaBytes) {
                storageEngine.deleteLocalFile(uid, ingested.localRelativePath)
                "Upload exceeds your 100 GB storage quota."
            }

            val userRef = db.collection("users").document(uid)
            val fileRef = userRef.collection("files").document(fileId)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val logRef = db.collection("activityLogs").document(logId)

            val filePayload = mapOf(
                "userId" to uid,
                "name" to safeName,
                "kind" to kind.name,
                "mimeType" to mimeType.take(120).ifBlank { "application/octet-stream" },
                "sizeBytes" to ingested.sizeBytes,
                "parentFolderId" to parentFolderId.take(128),
                "favorite" to false,
                "trashed" to false,
                "cloudSynced" to ingested.cloudByteSynced,
                "chunkCount" to ingested.base64Chunks.size.toLong(),
                "sha256" to ingested.sha256,
                "localRelativePath" to ingested.localRelativePath.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(fileRef, filePayload)
            batch.update(
                userRef,
                mapOf(
                    "usedBytes" to FieldValue.increment(ingested.sizeBytes),
                    "totalUploads" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "userEmail" to userEmail.take(160),
                    "action" to if (isBackup) LogAction.BACKUP.name else LogAction.UPLOAD.name,
                    "targetName" to safeName,
                    "bytes" to ingested.sizeBytes,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            ingested.base64Chunks.forEachIndexed { index, chunkBase64 ->
                val chunkRef = fileRef.collection("chunks").document("chunk_$index")
                batch.set(
                    chunkRef,
                    mapOf(
                        "userId" to uid,
                        "index" to index.toLong(),
                        "data" to chunkBase64,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()

            Result.success(
                VaultFile(
                    id = fileId,
                    userId = uid,
                    name = safeName,
                    kind = kind.name,
                    mimeType = mimeType,
                    sizeBytes = ingested.sizeBytes,
                    parentFolderId = parentFolderId,
                    cloudSynced = ingested.cloudByteSynced,
                    chunkCount = ingested.base64Chunks.size.toLong(),
                    sha256 = ingested.sha256,
                    localRelativePath = ingested.localRelativePath
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameFile(userId: String, fileId: String, newName: String): Result<Unit> {
        val path = "users/$userId/files/$fileId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val trimmed = newName.trim()
            require(trimmed.length in 1..200) { "Name must be between 1 and 200 characters" }
            db.collection("users").document(uid).collection("files").document(fileId)
                .update(
                    mapOf(
                        "name" to trimmed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun moveFile(userId: String, fileId: String, targetFolderId: String): Result<Unit> {
        val path = "users/$userId/files/$fileId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(fileId != targetFolderId) { "Cannot move a folder into itself" }
            db.collection("users").document(uid).collection("files").document(fileId)
                .update(
                    mapOf(
                        "parentFolderId" to targetFolderId.take(128),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(userId: String, fileId: String, favorite: Boolean): Result<Unit> {
        val path = "users/$userId/files/$fileId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid).collection("files").document(fileId)
                .update(
                    mapOf(
                        "favorite" to favorite,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setTrashed(
        userId: String,
        userEmail: String,
        file: VaultFile,
        trashed: Boolean
    ): Result<Unit> {
        val path = "users/$userId/files/${file.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val batch = db.batch()
            val fileRef = db.collection("users").document(uid).collection("files").document(file.id)
            batch.update(
                fileRef,
                mapOf(
                    "trashed" to trashed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("activityLogs").document(logId),
                mapOf(
                    "userId" to uid,
                    "userEmail" to userEmail.take(160),
                    "action" to if (trashed) LogAction.DELETE.name else LogAction.RESTORE.name,
                    "targetName" to file.name.take(200),
                    "bytes" to file.sizeBytes,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun permanentlyDeleteFile(userId: String, file: VaultFile): Result<Unit> {
        val path = "users/$userId/files/${file.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val userRef = db.collection("users").document(uid)
            val fileRef = userRef.collection("files").document(file.id)

            val chunksSnapshot = fileRef.collection("chunks").get().await()
            val batch = db.batch()
            chunksSnapshot.documents.forEach { chunkDoc ->
                batch.delete(chunkDoc.reference)
            }
            batch.delete(fileRef)
            if (file.sizeBytes > 0L) {
                batch.update(
                    userRef,
                    mapOf(
                        "usedBytes" to FieldValue.increment(-file.sizeBytes),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            storageEngine.deleteLocalFile(uid, file.localRelativePath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun emptyTrash(userId: String, trashedFiles: List<VaultFile>): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            for (file in trashedFiles) {
                val res = permanentlyDeleteFile(uid, file)
                if (res.isFailure) return res
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShareLink(
        userId: String,
        userEmail: String,
        file: VaultFile,
        expiresAtMillis: Long,
        rawPassword: String
    ): Result<SharedLink> {
        val linkId = "lnk_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "sharedLinks/$linkId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val trimmedPassword = rawPassword.trim()
            val passwordProtected = trimmedPassword.isNotEmpty()
            val passwordHash = if (passwordProtected) sha256String(trimmedPassword) else ""

            val payload = mapOf(
                "userId" to uid,
                "fileId" to file.id,
                "fileName" to file.name.take(200),
                "kind" to file.kind,
                "mimeType" to file.mimeType.take(120),
                "sizeBytes" to file.sizeBytes,
                "passwordProtected" to passwordProtected,
                "passwordHash" to passwordHash,
                "expiresAtMillis" to expiresAtMillis.coerceAtLeast(0L),
                "revoked" to false,
                "accessCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.set(db.collection("sharedLinks").document(linkId), payload)
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("activityLogs").document(logId),
                mapOf(
                    "userId" to uid,
                    "userEmail" to userEmail.take(160),
                    "action" to LogAction.SHARE.name,
                    "targetName" to file.name.take(200),
                    "bytes" to file.sizeBytes,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(
                SharedLink(
                    id = linkId,
                    userId = uid,
                    fileId = file.id,
                    fileName = file.name,
                    kind = file.kind,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    passwordProtected = passwordProtected,
                    passwordHash = passwordHash,
                    expiresAtMillis = expiresAtMillis,
                    revoked = false,
                    accessCount = 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleRevokeShareLink(linkId: String, revoked: Boolean): Result<Unit> {
        val path = "sharedLinks/$linkId"
        return try {
            requireUserId()
            db.collection("sharedLinks").document(linkId)
                .update(
                    mapOf(
                        "revoked" to revoked,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteShareLink(linkId: String): Result<Unit> {
        val path = "sharedLinks/$linkId"
        return try {
            requireUserId()
            db.collection("sharedLinks").document(linkId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveShareLink(linkId: String, passwordAttempt: String): Result<SharedLink> {
        val cleanId = linkId.trim()
            .removePrefix("https://lavbox.app/s/")
            .removePrefix("lavbox://share/")
            .trim()
        val path = "sharedLinks/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotBlank()) { "Enter a valid LavBox Share ID or URL" }
            val docRef = db.collection("sharedLinks").document(cleanId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Share link not found or revoked"))
            }
            val link = snap.toObject(
                SharedLink::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid shared link payload"))

            if (link.revoked) {
                return Result.failure(IllegalStateException("This shared link has been revoked by the owner."))
            }
            if (link.expired) {
                return Result.failure(IllegalStateException("This shared link has expired."))
            }
            if (link.passwordProtected) {
                require(passwordAttempt.isNotBlank()) { "Password required to unlock this shared link." }
                val attemptHash = sha256String(passwordAttempt.trim())
                if (attemptHash != link.passwordHash) {
                    return Result.failure(IllegalStateException("Incorrect password for shared link."))
                }
            }
            docRef.update(
                mapOf(
                    "accessCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(link.copy(accessCount = link.accessCount + 1L))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun ensureLocalFileForPreviewOrExport(userId: String, file: VaultFile): Result<File> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val localFile = storageEngine.resolveLocalFile(uid, file.localRelativePath)
            if (localFile.exists() && localFile.length() > 0L) {
                return Result.success(localFile)
            }
            if (file.cloudSynced && file.chunkCount > 0L) {
                val chunksSnap = db.collection("users").document(uid)
                    .collection("files").document(file.id)
                    .collection("chunks")
                    .orderBy("index", Query.Direction.ASCENDING)
                    .get()
                    .await()
                val chunks = chunksSnap.toObjects(FileChunk::class.java)
                if (chunks.isNotEmpty()) {
                    val restored = storageEngine.writeChunksToLocalFile(
                        uid = uid,
                        relativePath = file.localRelativePath.ifBlank { "${file.id}_${file.name}" },
                        base64Chunks = chunks.sortedBy { it.index }.map { it.data }
                    )
                    return Result.success(restored)
                }
            }
            Result.failure(IllegalStateException("File binary is stored on the original upload device."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId/files/${file.id}/chunks")
            Result.failure(e)
        }
    }

    suspend fun exportFileToUri(
        userId: String,
        userEmail: String,
        file: VaultFile,
        destinationUri: Uri
    ): Result<Long> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val localFile = ensureLocalFileForPreviewOrExport(uid, file).getOrThrow()
            val bytes = storageEngine.exportLocalFileToUri(localFile, destinationUri)
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "totalDownloads" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                db.collection("activityLogs").document(logId),
                mapOf(
                    "userId" to uid,
                    "userEmail" to userEmail.take(160),
                    "action" to LogAction.DOWNLOAD.name,
                    "targetName" to file.name.take(200),
                    "bytes" to bytes,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBackupSettings(
        userId: String,
        autoBackupPhotos: Boolean,
        autoBackupVideos: Boolean,
        wifiOnlyBackup: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "autoBackupPhotos" to autoBackupPhotos,
                        "autoBackupVideos" to autoBackupVideos,
                        "wifiOnlyBackup" to wifiOnlyBackup,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logPlanInquiry(userId: String, userEmail: String, tier: PlanTier): Result<Unit> {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("activityLogs").document(logId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "userEmail" to userEmail.take(160),
                        "action" to LogAction.PLAN_INQUIRY.name,
                        "targetName" to "${tier.title} (${tier.monthlyPrice})",
                        "bytes" to tier.quotaBytes,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "activityLogs/$logId")
            Result.failure(e)
        }
    }

    // --- Admin Operations (Enforced Server-Side by firestore.rules isAdmin()) ---

    suspend fun checkIsAdmin(uid: String, email: String?, emailVerified: Boolean): Boolean {
        if (email == "lavkeshkumre88@gmail.com" && emailVerified) return true
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchAllUsersForAdmin(): Result<List<UserAccount>> {
        return try {
            requireUserId()
            val snap = db.collection("users").get().await()
            val users = snap.toObjects(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.usedBytes }
            Result.success(users)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            Result.failure(e)
        }
    }

    suspend fun fetchGlobalActivityLogsForAdmin(): Result<List<ActivityLog>> {
        return try {
            requireUserId()
            val snap = db.collection("activityLogs").limit(100).get().await()
            val logs = snap.toObjects(
                ActivityLog::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            Result.success(logs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "activityLogs")
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserSubscriptionAndStatus(
        targetUserId: String,
        newTier: PlanTier,
        newStatus: String
    ): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            val quota = when (newTier) {
                PlanTier.FREE -> FREE_QUOTA_BYTES
                PlanTier.PRO_1TB -> PRO_QUOTA_BYTES
                PlanTier.ULTRA_2TB -> ULTRA_QUOTA_BYTES
            }
            db.collection("users").document(targetUserId)
                .update(
                    mapOf(
                        "planTier" to newTier.name,
                        "quotaBytes" to quota,
                        "accountStatus" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun inspectPickedUri(uri: Uri): PickedFileMetadata? = storageEngine.inspectUri(uri)
}

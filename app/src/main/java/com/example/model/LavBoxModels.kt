package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val FREE_QUOTA_BYTES: Long = 107_374_182_400L // 100 GB
const val PRO_QUOTA_BYTES: Long = 1_099_511_627_776L // 1 TB
const val ULTRA_QUOTA_BYTES: Long = 2_199_023_255_552L // 2 TB

enum class PlanTier(
    val title: String,
    val quotaBytes: Long,
    val monthlyPrice: String,
    val yearlyPrice: String,
    val tagline: String
) {
    FREE("Free Vault", FREE_QUOTA_BYTES, "$0", "$0", "100 GB Free Cloud Storage"),
    PRO_1TB("LavBox Pro", PRO_QUOTA_BYTES, "$4.99/mo", "$49.99/yr", "1 TB Priority Sync & Link Controls"),
    ULTRA_2TB("LavBox Ultra", ULTRA_QUOTA_BYTES, "$9.99/mo", "$99.99/yr", "2 TB Studio Vault & Audit Telemetry");

    companion object {
        fun fromRaw(raw: String?): PlanTier =
            entries.firstOrNull { it.name == raw } ?: FREE
    }
}

enum class FileKind(val label: String) {
    FOLDER("Folder"),
    PHOTO("Photo"),
    VIDEO("Video"),
    PDF("PDF"),
    DOCUMENT("Document");

    companion object {
        fun fromRaw(raw: String?): FileKind =
            entries.firstOrNull { it.name == raw } ?: DOCUMENT
    }
}

enum class LogAction(val label: String) {
    UPLOAD("Upload"),
    DOWNLOAD("Download"),
    SHARE("Share Link"),
    DELETE("Delete"),
    RESTORE("Restore"),
    PLAN_INQUIRY("Plan Upgrade Check"),
    BACKUP("Auto Backup");

    companion object {
        fun fromRaw(raw: String?): LogAction =
            entries.firstOrNull { it.name == raw } ?: UPLOAD
    }
}

enum class FileSortOrder(val label: String) {
    NEWEST("Newest First"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest Size"),
    KIND("File Type")
}

enum class FileCategoryFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    PDFS("PDFs"),
    DOCUMENTS("Docs"),
    FAVORITES("Favorites"),
    TRASH("Trash")
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val planTier: String = PlanTier.FREE.name,
    val quotaBytes: Long = FREE_QUOTA_BYTES,
    val usedBytes: Long = 0L,
    val totalUploads: Long = 0L,
    val totalDownloads: Long = 0L,
    val role: String = "USER",
    val accountStatus: String = "ACTIVE",
    val autoBackupPhotos: Boolean = true,
    val autoBackupVideos: Boolean = false,
    val wifiOnlyBackup: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val tierEnum: PlanTier get() = PlanTier.fromRaw(planTier)
    val remainingBytes: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0L)
    val usageFraction: Float get() = if (quotaBytes <= 0L) 0f else (usedBytes.toDouble() / quotaBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

@Keep
@IgnoreExtraProperties
data class VaultFile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val kind: String = FileKind.DOCUMENT.name,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val parentFolderId: String = "",
    val favorite: Boolean = false,
    val trashed: Boolean = false,
    val cloudSynced: Boolean = false,
    val chunkCount: Long = 0L,
    val sha256: String = "",
    val localRelativePath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val kindEnum: FileKind get() = FileKind.fromRaw(kind)
    val folderFlag: Boolean get() = kindEnum == FileKind.FOLDER
}

@Keep
@IgnoreExtraProperties
data class FileChunk(
    @DocumentId val id: String = "",
    val userId: String = "",
    val index: Long = 0L,
    val data: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SharedLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileId: String = "",
    val fileName: String = "",
    val kind: String = FileKind.DOCUMENT.name,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val passwordProtected: Boolean = false,
    val passwordHash: String = "",
    val expiresAtMillis: Long = 0L,
    val revoked: Boolean = false,
    val accessCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val kindEnum: FileKind get() = FileKind.fromRaw(kind)
    val expired: Boolean get() = expiresAtMillis > 0L && System.currentTimeMillis() > expiresAtMillis
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val action: String = LogAction.UPLOAD.name,
    val targetName: String = "",
    val bytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val actionEnum: LogAction get() = LogAction.fromRaw(action)
}

enum class TransferStatus(val label: String) {
    QUEUED("Queued"),
    UPLOADING("Uploading"),
    PAUSED("Paused"),
    WAITING_NETWORK("Waiting for Network"),
    COMPLETED("Synced"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class UploadTaskState(
    val taskId: String,
    val fileName: String,
    val mimeType: String,
    val kind: FileKind,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val backupTask: Boolean = false,
    val parentFolderId: String = "",
    val sourceUriString: String = "",
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

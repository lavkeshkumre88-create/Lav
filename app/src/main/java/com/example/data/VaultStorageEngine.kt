package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class PickedFileMetadata(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class IngestedVaultFile(
    val fileId: String,
    val localFile: File,
    val localRelativePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val base64Chunks: List<String>,
    val cloudByteSynced: Boolean
)

class VaultStorageEngine(private val context: Context) {

    companion object {
        // Files up to 1.2 MB are chunked (380 KB raw -> ~506 KB base64) and persisted directly in Firestore chunks
        const val CLOUD_INLINE_MAX_BYTES: Long = 1_200_000L
        const val RAW_CHUNK_SIZE: Int = 380_000
    }

    private fun userVaultDir(uid: String): File {
        val dir = File(context.filesDir, "vault/$uid")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun resolveLocalFile(uid: String, relativePath: String): File {
        val clean = relativePath.removePrefix("vault/").removePrefix("$uid/")
        return File(userVaultDir(uid), clean)
    }

    fun inspectUri(uri: Uri): PickedFileMetadata? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var name = "LavBox_File_${System.currentTimeMillis()}"
        var size = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return PickedFileMetadata(uri = uri, displayName = name, mimeType = mimeType, sizeBytes = size.coerceAtLeast(0L))
    }

    suspend fun ingestFromUri(
        uid: String,
        fileId: String,
        uri: Uri,
        sanitizedFileName: String,
        expectedBytes: Long,
        isPaused: () -> Boolean,
        onProgress: (transferred: Long, total: Long) -> Unit
    ): IngestedVaultFile = withContext(Dispatchers.IO) {
        val targetDir = userVaultDir(uid)
        val safeName = sanitizedFileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val relativeFileName = "${fileId}_$safeName"
        val destFile = File(targetDir, relativeFileName)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var totalCopied = 0L

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        while (isPaused()) {
                            coroutineContext.ensureActive()
                            kotlinx.coroutines.delay(250L)
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalCopied += read
                        onProgress(totalCopied, maxOf(expectedBytes, totalCopied))
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("Cannot open selected file stream")
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            throw e
        }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val finalSize = destFile.length()

        val chunks = if (finalSize in 1L..CLOUD_INLINE_MAX_BYTES) {
            val bytes = destFile.readBytes()
            val list = mutableListOf<String>()
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + RAW_CHUNK_SIZE, bytes.size)
                val slice = bytes.copyOfRange(offset, end)
                list.add(Base64.encodeToString(slice, Base64.NO_WRAP))
                offset = end
            }
            list
        } else {
            emptyList()
        }

        IngestedVaultFile(
            fileId = fileId,
            localFile = destFile,
            localRelativePath = "vault/$uid/$relativeFileName",
            sizeBytes = finalSize,
            sha256 = sha256,
            base64Chunks = chunks,
            cloudByteSynced = chunks.isNotEmpty()
        )
    }

    suspend fun writeChunksToLocalFile(uid: String, relativePath: String, base64Chunks: List<String>): File =
        withContext(Dispatchers.IO) {
            val dest = resolveLocalFile(uid, relativePath)
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                for (chunk in base64Chunks) {
                    val decoded = Base64.decode(chunk, Base64.DEFAULT)
                    out.write(decoded)
                }
                out.flush()
            }
            dest
        }

    suspend fun exportLocalFileToUri(localFile: File, targetUri: Uri): Long = withContext(Dispatchers.IO) {
        if (!localFile.exists()) throw IllegalStateException("File is not cached on this device yet")
        var copied = 0L
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            localFile.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    copied += read
                }
            }
            out.flush()
        } ?: throw IllegalStateException("Unable to open export destination")
        copied
    }

    fun deleteLocalFile(uid: String, relativePath: String) {
        if (relativePath.isBlank()) return
        val file = resolveLocalFile(uid, relativePath)
        if (file.exists()) file.delete()
    }
}

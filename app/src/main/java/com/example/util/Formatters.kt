package com.example.util

import com.example.model.FileKind
import com.google.firebase.Timestamp
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups >= 3) {
        String.format(Locale.US, "%.2f %s", value, units[digitGroups])
    } else if (digitGroups == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

fun formatTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Just now"
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(date)
}

fun formatExpiryMillis(millis: Long): String {
    if (millis <= 0L) return "Never expires"
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(millis))
}

fun sha256String(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

fun determineFileKind(mimeType: String, fileName: String): FileKind {
    val lowerMime = mimeType.lowercase(Locale.US)
    val lowerName = fileName.lowercase(Locale.US)
    return when {
        lowerMime == "inode/directory" -> FileKind.FOLDER
        lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".heic") -> FileKind.PHOTO
        lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") ||
            lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") -> FileKind.VIDEO
        lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> FileKind.PDF
        else -> FileKind.DOCUMENT
    }
}

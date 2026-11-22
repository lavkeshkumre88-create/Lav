package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.FileKind
import com.example.model.VaultFile
import com.example.ui.components.GlassCard
import com.example.ui.components.VaultFileBadgeIcon
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes
import com.example.util.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaViewerScreen(
    file: VaultFile,
    prepareFile: (VaultFile, (Result<File>) -> Unit) -> Unit,
    onDownload: (VaultFile) -> Unit,
    onShare: (VaultFile) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var localFile by remember(file.id) { mutableStateOf<File?>(null) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var errorMessage by remember(file.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.id) {
        loading = true
        errorMessage = null
        prepareFile(file) { result ->
            loading = false
            result.onSuccess { localFile = it }
                .onFailure { errorMessage = it.localizedMessage ?: "Preview unavailable" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Viewer Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("viewer_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(file.sizeBytes)} · ${file.kindEnum.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LuminousLavender
                )
            }
            IconButton(
                onClick = { onShare(file) },
                modifier = Modifier.size(48.dp).testTag("viewer_share_btn")
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share link", tint = LuminousLavender)
            }
            IconButton(
                onClick = { onDownload(file) },
                modifier = Modifier.size(48.dp).testTag("viewer_download_btn")
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download file", tint = TextPrimary)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CircularProgressIndicator(color = RoyalPurple)
                errorMessage != null -> {
                    GlassCard(modifier = Modifier.padding(24.dp)) {
                        Text(errorMessage!!, color = CrimsonAlert)
                    }
                }
                localFile != null -> {
                    val resolvedFile = localFile!!
                    when (file.kindEnum) {
                        FileKind.PHOTO -> ZoomableImagePreview(resolvedFile, file.name)
                        FileKind.VIDEO -> VideoPlayerPreview(resolvedFile)
                        FileKind.PDF -> PdfDocumentPreview(resolvedFile)
                        else -> DocumentTextOrInfoPreview(file, resolvedFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePreview(file: File, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun VideoPlayerPreview(file: File) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.fromFile(file))
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    )
}

@Composable
private fun PdfDocumentPreview(file: File) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfError by remember { mutableStateOf<String?>(null) }

    val rendererHolder = remember(file.absolutePath) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
            pfd to renderer
        } catch (e: Exception) {
            pdfError = e.localizedMessage ?: "Unable to render PDF"
            null
        }
    }

    DisposableEffect(rendererHolder) {
        onDispose {
            try {
                rendererHolder?.second?.close()
                rendererHolder?.first?.close()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(rendererHolder, pageIndex) {
        val renderer = rendererHolder?.second ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        val safeIdx = pageIndex.coerceIn(0, pageCount - 1)
        val bmp = withContext(Dispatchers.IO) {
            renderer.openPage(safeIdx).use { page ->
                val targetW = (page.width * 2).coerceIn(600, 1800)
                val targetH = (page.height * (targetW.toFloat() / page.width.toFloat())).toInt().coerceIn(600, 2400)
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
        renderedBitmap = bmp
    }

    if (pdfError != null) {
        GlassCard(modifier = Modifier.padding(24.dp)) {
            Text(pdfError!!, color = CrimsonAlert)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                renderedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                    )
                } ?: CircularProgressIndicator(color = RoyalPurple)
            }

            if (pageCount > 1) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        IconButton(
                            onClick = { if (pageIndex > 0) pageIndex-- },
                            enabled = pageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page", tint = TextPrimary)
                        }
                        Text(
                            text = "Page ${pageIndex + 1} of $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                            enabled = pageIndex < pageCount - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page", tint = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTextOrInfoPreview(vaultFile: VaultFile, file: File) {
    val textContent = remember(file.absolutePath, vaultFile.mimeType) {
        val isText = vaultFile.mimeType.startsWith("text/") ||
            vaultFile.name.endsWith(".txt", true) ||
            vaultFile.name.endsWith(".md", true) ||
            vaultFile.name.endsWith(".json", true) ||
            vaultFile.name.endsWith(".csv", true)
        if (isText && file.length() <= 200_000L) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VaultFileBadgeIcon(kind = vaultFile.kindEnum, size = 54.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(vaultFile.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(vaultFile.mimeType, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Size: ${formatBytes(vaultFile.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Updated: ${formatTimestamp(vaultFile.updatedAt ?: vaultFile.createdAt)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (vaultFile.sha256.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SHA-256: ${vaultFile.sha256}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LuminousLavender
                )
            }
        }

        if (textContent != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = textContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.FileKind
import com.example.model.VaultFile
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.NeonOrchid
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes
import com.example.util.formatTimestamp

@Composable
fun LavBoxLogoMark(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Outer glowing violet aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ElectricViolet.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f
        )

        // Cloud silhouette base with 3D purple gradient
        val cloudPath = Path().apply {
            moveTo(w * 0.24f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.72f)
            cubicTo(w * 0.90f, h * 0.72f, w * 0.94f, h * 0.55f, w * 0.82f, h * 0.48f)
            cubicTo(w * 0.80f, h * 0.29f, w * 0.57f, h * 0.20f, w * 0.44f, h * 0.34f)
            cubicTo(w * 0.31f, h * 0.28f, w * 0.16f, h * 0.40f, w * 0.19f, h * 0.53f)
            cubicTo(w * 0.10f, h * 0.56f, w * 0.12f, h * 0.72f, w * 0.24f, h * 0.72f)
            close()
        }

        drawPath(
            path = cloudPath,
            brush = Brush.linearGradient(
                colors = listOf(LuminousLavender, RoyalPurple, DeepAmethyst),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = cloudPath,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(width = w * 0.03f)
        )

        // Inner 3D Vault Cube
        val cx = w * 0.51f
        val cy = h * 0.53f
        val r = w * 0.14f
        val cubeTop = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.88f, cy - r * 0.48f)
            lineTo(cx, cy)
            lineTo(cx - r * 0.88f, cy - r * 0.48f)
            close()
        }
        drawPath(cubeTop, color = Color.White.copy(alpha = 0.92f))

        val cubeLeft = Path().apply {
            moveTo(cx - r * 0.88f, cy - r * 0.48f)
            lineTo(cx, cy)
            lineTo(cx, cy + r * 0.95f)
            lineTo(cx - r * 0.88f, cy + r * 0.46f)
            close()
        }
        drawPath(cubeLeft, color = Color(0xFFD8B4FE))

        val cubeRight = Path().apply {
            moveTo(cx + r * 0.88f, cy - r * 0.48f)
            lineTo(cx, cy)
            lineTo(cx, cy + r * 0.95f)
            lineTo(cx + r * 0.88f, cy + r * 0.46f)
            close()
        }
        drawPath(cubeRight, color = DeepAmethyst)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    glowAccent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (glowAccent) {
        Brush.linearGradient(
            colors = listOf(ElectricViolet.copy(alpha = 0.75f), NeonOrchid.copy(alpha = 0.35f), GlassBorder)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(GlassBorder, Color.White.copy(alpha = 0.07f))
        )
    }

    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            GlassSurface.copy(alpha = 0.92f),
            MidnightVoid.copy(alpha = 0.95f)
        )
    )

    val cardModifier = modifier
        .clip(shape)
        .background(backgroundBrush)
        .border(BorderStroke(1.dp, borderBrush), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

@Composable
fun StorageGradientProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 220f),
        label = "storage_bar"
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
    ) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            size = size
        )
        val fillWidth = (size.width * animatedFraction).coerceAtLeast(if (animatedFraction > 0f) size.height else 0f)
        if (fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(RoyalPurple, ElectricViolet, NeonOrchid)
                ),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                size = Size(fillWidth, size.height)
            )
        }
    }
}

fun fileKindVisual(kind: FileKind): Pair<ImageVector, Color> {
    return when (kind) {
        FileKind.FOLDER -> Icons.Filled.Folder to AmberPending
        FileKind.PHOTO -> Icons.Filled.Image to ElectricViolet
        FileKind.VIDEO -> Icons.Filled.Videocam to NeonOrchid
        FileKind.PDF -> Icons.Filled.PictureAsPdf to CrimsonAlert
        FileKind.DOCUMENT -> Icons.Filled.Description to SkyInfo
    }
}

@Composable
fun VaultFileBadgeIcon(
    kind: FileKind,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val (icon, tint) = fileKindVisual(kind)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = kind.label,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun VaultFileRow(
    file: VaultFile,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDownload: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onPermanentDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_row_${file.id}"),
        shape = RoundedCornerShape(18.dp),
        color = GlassSurface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.65f)),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaultFileBadgeIcon(kind = file.kindEnum)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (file.folderFlag) "Folder" else formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousLavender
                    )
                    Text("·", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = formatTimestamp(file.updatedAt ?: file.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!file.folderFlag) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = if (file.cloudSynced) EmeraldSync else SkyInfo,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (file.cloudSynced) "Cloud Synced" else "Vault Indexed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (file.cloudSynced) EmeraldSync else SkyInfo
                        )
                    }
                }
            }

            if (!file.trashed) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fav_btn_${file.id}")
                ) {
                    Icon(
                        imageVector = if (file.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (file.favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (file.favorite) AmberPending else TextMuted
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("more_btn_${file.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "File options",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!file.trashed) {
                        if (!file.folderFlag) {
                            DropdownMenuItem(
                                text = { Text("Share Link") },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download / Export") },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Trash", color = CrimsonAlert) },
                            onClick = {
                                menuExpanded = false
                                onTrashOrRestore()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = {
                                menuExpanded = false
                                onTrashOrRestore()
                            }
                        )
                        if (onPermanentDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete Forever", color = CrimsonAlert) },
                                onClick = {
                                    menuExpanded = false
                                    onPermanentDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = LuminousLavender,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

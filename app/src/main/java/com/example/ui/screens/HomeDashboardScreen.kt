package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.FileCategoryFilter
import com.example.model.FileKind
import com.example.model.SharedLink
import com.example.model.TransferStatus
import com.example.model.UiState
import com.example.model.UploadTaskState
import com.example.model.UserAccount
import com.example.model.VaultFile
import com.example.ui.components.GlassCard
import com.example.ui.components.LavBoxLogoMark
import com.example.ui.components.SectionHeader
import com.example.ui.components.StorageGradientProgressBar
import com.example.ui.components.VaultFileRow
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.NeonOrchid
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes

@Composable
fun HomeDashboardScreen(
    accountState: UiState<UserAccount?>,
    filesState: UiState<List<VaultFile>>,
    sharedLinksState: UiState<List<SharedLink>>,
    uploadTasks: List<UploadTaskState>,
    onNavigateToUpload: () -> Unit,
    onNavigateToCategory: (FileCategoryFilter) -> Unit,
    onNavigateToShared: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenFile: (VaultFile) -> Unit,
    onToggleFavorite: (VaultFile) -> Unit,
    onShareFile: (VaultFile) -> Unit,
    onRenameFile: (VaultFile) -> Unit,
    onMoveFile: (VaultFile) -> Unit,
    onDownloadFile: (VaultFile) -> Unit,
    onTrashFile: (VaultFile) -> Unit
) {
    val account = (accountState as? UiState.Success)?.data
    val allFiles = (filesState as? UiState.Success)?.data ?: emptyList()
    val sharedLinks = (sharedLinksState as? UiState.Success)?.data ?: emptyList()

    val activeFiles = allFiles.filter { !it.trashed }
    val photosCount = activeFiles.count { it.kindEnum == FileKind.PHOTO }
    val videosCount = activeFiles.count { it.kindEnum == FileKind.VIDEO }
    val docsCount = activeFiles.count { it.kindEnum == FileKind.PDF || it.kindEnum == FileKind.DOCUMENT }
    val favoritesCount = activeFiles.count { it.favorite }
    val trashCount = allFiles.count { it.trashed }
    val sharedCount = sharedLinks.count { !it.revoked }

    val recentFiles = activeFiles
        .filter { !it.folderFlag }
        .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
        .take(8)

    val activeTransfers = uploadTasks.count {
        it.status == TransferStatus.UPLOADING ||
            it.status == TransferStatus.QUEUED ||
            it.status == TransferStatus.WAITING_NETWORK
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Logo, Welcome, and Profile Avatar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    LavBoxLogoMark(size = 42.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = account?.displayName ?: "LavBox Vault",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(RoyalPurple, NeonOrchid))
                        )
                        .clickable(onClick = onNavigateToProfile)
                        .testTag("home_profile_avatar"),
                    contentAlignment = Alignment.Center
                ) {
                    if (!account?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = account.photoUrl,
                            contentDescription = "User profile avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (account?.displayName?.firstOrNull() ?: 'L').uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 100 GB Storage Glass Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowAccent = true
            ) {
                if (accountState is UiState.Loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Syncing 100 GB cloud quota...", color = TextSecondary)
                    }
                } else if (accountState is UiState.Error) {
                    Text(accountState.message, color = CrimsonAlert)
                } else if (account != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = account.tierEnum.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminousLavender
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatBytes(account.usedBytes)} used",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = DeepAmethyst.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.7f)),
                            onClick = onNavigateToUpgrade,
                            modifier = Modifier.testTag("home_upgrade_chip")
                        ) {
                            Text(
                                text = if (account.planTier == "FREE") "Upgrade" else "Pro Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    StorageGradientProgressBar(fraction = account.usageFraction)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remaining: ${formatBytes(account.remainingBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldSync
                        )
                        Text(
                            text = "Quota: ${formatBytes(account.quotaBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToUpload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("home_upload_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalPurple,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload to LavBox", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Auto Backup Banner
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToUpload
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldSync.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Backup,
                                contentDescription = null,
                                tint = EmeraldSync
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Auto Backup & Transfer Queue",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = if (activeTransfers > 0) {
                                    "$activeTransfers active transfer(s) in queue"
                                } else if (account?.autoBackupPhotos == true) {
                                    "Photo backup armed · ${if (account.wifiOnlyBackup) "Wi-Fi only" else "All networks"}"
                                } else {
                                    "Configure photo & video backup"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium,
                        color = LuminousLavender
                    )
                }
            }
        }

        // 6 Category Quick Access Tiles (Photos, Videos, Documents, Favorites, Shared, Trash)
        item {
            SectionHeader(
                title = "Vault Categories",
                actionLabel = "Browse All",
                onAction = { onNavigateToCategory(FileCategoryFilter.ALL) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryTile(
                        title = "Photos",
                        count = photosCount,
                        icon = Icons.Filled.Image,
                        tint = ElectricViolet,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_photos",
                        onClick = { onNavigateToCategory(FileCategoryFilter.PHOTOS) }
                    )
                    CategoryTile(
                        title = "Videos",
                        count = videosCount,
                        icon = Icons.Filled.Videocam,
                        tint = NeonOrchid,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_videos",
                        onClick = { onNavigateToCategory(FileCategoryFilter.VIDEOS) }
                    )
                    CategoryTile(
                        title = "Docs",
                        count = docsCount,
                        icon = Icons.Filled.Description,
                        tint = SkyInfo,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_docs",
                        onClick = { onNavigateToCategory(FileCategoryFilter.DOCUMENTS) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryTile(
                        title = "Favorites",
                        count = favoritesCount,
                        icon = Icons.Filled.Star,
                        tint = AmberPending,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_favorites",
                        onClick = { onNavigateToCategory(FileCategoryFilter.FAVORITES) }
                    )
                    CategoryTile(
                        title = "Shared",
                        count = sharedCount,
                        icon = Icons.Filled.Share,
                        tint = EmeraldSync,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_shared",
                        onClick = onNavigateToShared
                    )
                    CategoryTile(
                        title = "Trash",
                        count = trashCount,
                        icon = Icons.Filled.DeleteOutline,
                        tint = CrimsonAlert,
                        modifier = Modifier.weight(1f),
                        testTag = "cat_trash",
                        onClick = { onNavigateToCategory(FileCategoryFilter.TRASH) }
                    )
                }
            }
        }

        // Recent Files
        item {
            SectionHeader(
                title = "Recent Files",
                actionLabel = if (recentFiles.isNotEmpty()) "See All" else null,
                onAction = { onNavigateToCategory(FileCategoryFilter.ALL) }
            )
        }

        if (filesState is UiState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalPurple)
                }
            }
        } else if (recentFiles.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = LuminousLavender,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No files in your cloud vault yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap \"Upload to LavBox\" above to add photos, videos, PDFs, or documents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(recentFiles, key = { it.id }) { file ->
                VaultFileRow(
                    file = file,
                    onOpen = { onOpenFile(file) },
                    onToggleFavorite = { onToggleFavorite(file) },
                    onShare = { onShareFile(file) },
                    onRename = { onRenameFile(file) },
                    onMove = { onMoveFile(file) },
                    onDownload = { onDownloadFile(file) },
                    onTrashOrRestore = { onTrashFile(file) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = GlassSurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, GlassBorder),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = "$count items",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

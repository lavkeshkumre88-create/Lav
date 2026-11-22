package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.NetworkStatus
import com.example.model.TransferStatus
import com.example.model.UiState
import com.example.model.UploadTaskState
import com.example.model.UserAccount
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.VaultFileBadgeIcon
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes

@Composable
fun UploadCenterScreen(
    accountState: UiState<UserAccount?>,
    networkStatus: NetworkStatus,
    uploadTasks: List<UploadTaskState>,
    onEnqueueUploads: (uris: List<Uri>, isBackup: Boolean) -> Unit,
    onPauseOrResume: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onUpdateBackupPreferences: (autoPhotos: Boolean, autoVideos: Boolean, wifiOnly: Boolean) -> Unit
) {
    val account = (accountState as? UiState.Success)?.data

    // Zero-permission Android Photo Picker for Photos & Videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25),
        onResult = { uris ->
            if (uris.isNotEmpty()) onEnqueueUploads(uris, false)
        }
    )

    // Zero-permission Android Photo Picker for Automatic Media Backup Batch
    val backupMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40),
        onResult = { uris ->
            if (uris.isNotEmpty()) onEnqueueUploads(uris, true)
        }
    )

    // Zero-permission Storage Access Framework picker for PDFs & Documents
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onEnqueueUploads(uris, false)
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Upload & Backup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (account != null) {
                            "${formatBytes(account.remainingBytes)} remaining in vault"
                        } else {
                            "Syncing quota..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (networkStatus.connected) {
                        if (networkStatus.unmeteredWifi) EmeraldSync.copy(alpha = 0.16f) else SkyInfo.copy(alpha = 0.16f)
                    } else {
                        CrimsonAlert.copy(alpha = 0.16f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (networkStatus.connected) EmeraldSync.copy(alpha = 0.45f) else CrimsonAlert.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (networkStatus.connected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = if (networkStatus.connected) EmeraldSync else CrimsonAlert,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                !networkStatus.connected -> "Offline"
                                networkStatus.unmeteredWifi -> "Wi-Fi Ready"
                                else -> "Cellular"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Upload Action Cards
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowAccent = true
            ) {
                Text(
                    text = "Direct Cloud Upload",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uses Android's privacy-safe Photo Picker & Document Provider with SHA-256 integrity checks and quota validation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("pick_media_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos & Videos", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/*",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("pick_docs_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = LuminousLavender)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDFs & Docs", color = LuminousLavender, maxLines = 1)
                    }
                }
            }
        }

        // Automatic Backup Settings & Trigger
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Backup, contentDescription = null, tint = ElectricViolet)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Automatic Media Backup",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (account != null) {
                    BackupSwitchRow(
                        title = "Photo Backup",
                        subtitle = "Queue camera & gallery photos for LavBox vault",
                        checked = account.autoBackupPhotos,
                        onCheckedChange = {
                            onUpdateBackupPreferences(it, account.autoBackupVideos, account.wifiOnlyBackup)
                        }
                    )
                    BackupSwitchRow(
                        title = "Video Backup",
                        subtitle = "Include HD/4K videos in backup batches",
                        checked = account.autoBackupVideos,
                        onCheckedChange = {
                            onUpdateBackupPreferences(account.autoBackupPhotos, it, account.wifiOnlyBackup)
                        }
                    )
                    BackupSwitchRow(
                        title = "Wi-Fi Only Backup",
                        subtitle = "Automatically pause backup transfers on cellular data",
                        checked = account.wifiOnlyBackup,
                        onCheckedChange = {
                            onUpdateBackupPreferences(account.autoBackupPhotos, account.autoBackupVideos, it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val requestType = when {
                            account?.autoBackupPhotos == true && account.autoBackupVideos ->
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            account?.autoBackupVideos == true ->
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            else ->
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                        }
                        backupMediaLauncher.launch(PickVisualMediaRequest(requestType))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_backup_batch_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = LuminousLavender)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Media Batch to Back Up", color = LuminousLavender)
                }
            }
        }

        // Transfer Queue
        item {
            SectionHeader(
                title = "Transfer Queue (${uploadTasks.size})",
                actionLabel = if (uploadTasks.any {
                        it.status == TransferStatus.COMPLETED || it.status == TransferStatus.CANCELLED
                    }
                ) "Clear Done" else null,
                onAction = onClearCompleted
            )
        }

        if (uploadTasks.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No active transfers. Files up to 1.2 MB sync full binary chunks to Cloud Firestore alongside local vault caching; larger files index metadata & SHA-256 in the cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(uploadTasks, key = { it.taskId }) { task ->
                TransferTaskRow(
                    task = task,
                    onPauseOrResume = { onPauseOrResume(task.taskId) },
                    onCancel = { onCancelTask(task.taskId) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun BackupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RoyalPurple
            )
        )
    }
}

@Composable
private fun TransferTaskRow(
    task: UploadTaskState,
    onPauseOrResume: () -> Unit,
    onCancel: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultFileBadgeIcon(kind = task.kind, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${formatBytes(task.transferredBytes)} / ${formatBytes(task.totalBytes)} · ${task.status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (task.status) {
                        TransferStatus.COMPLETED -> EmeraldSync
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> CrimsonAlert
                        TransferStatus.PAUSED, TransferStatus.WAITING_NETWORK -> AmberPending
                        else -> LuminousLavender
                    }
                )
                if (task.errorMessage != null) {
                    Text(
                        text = task.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert
                    )
                }
            }

            if (task.status == TransferStatus.UPLOADING ||
                task.status == TransferStatus.PAUSED ||
                task.status == TransferStatus.WAITING_NETWORK ||
                task.status == TransferStatus.FAILED
            ) {
                IconButton(onClick = onPauseOrResume, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (task.status == TransferStatus.UPLOADING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (task.status == TransferStatus.UPLOADING) "Pause upload" else "Resume upload",
                        tint = LuminousLavender
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "Cancel upload",
                        tint = TextMuted
                    )
                }
            }
        }

        if (task.status == TransferStatus.UPLOADING || task.status == TransferStatus.PAUSED) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = RoyalPurple,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

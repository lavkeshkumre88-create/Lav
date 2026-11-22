package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SharedLink
import com.example.model.UiState
import com.example.ui.components.GlassCard
import com.example.ui.components.VaultFileBadgeIcon
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes
import com.example.util.formatExpiryMillis

@Composable
fun SharedLinksScreen(
    sharedLinksState: UiState<List<SharedLink>>,
    onToggleRevoke: (SharedLink) -> Unit,
    onDeleteLink: (SharedLink) -> Unit,
    onResolveLink: (linkIdOrUrl: String, password: String, onResult: (Result<SharedLink>) -> Unit) -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showResolverDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shared Vault Links",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Manage expiration, SHA-256 passwords & instant access revocation",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                OutlinedButton(
                    onClick = { showResolverDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.testTag("open_shared_link_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuminousLavender)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify Link", color = LuminousLavender)
                }
            }
        }

        when (sharedLinksState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RoyalPurple)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(sharedLinksState.message, color = CrimsonAlert)
                    }
                }
            }
            is UiState.Success -> {
                val links = sharedLinksState.data
                if (links.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    tint = LuminousLavender,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No shared links yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Open any file's menu in Files and tap \"Share Link\" to generate a protected LavBox URL.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(links, key = { it.id }) { link ->
                        val shareUrl = "https://lavbox.app/s/${link.id}"
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shared_link_card_${link.id}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VaultFileBadgeIcon(kind = link.kindEnum, size = 42.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = link.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatBytes(link.sizeBytes)} · ${link.accessCount} opens",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuminousLavender
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(shareUrl))
                                        onNotify("Copied link ID: ${link.id}")
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy share link", tint = LuminousLavender)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = shareUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = when {
                                        link.revoked -> "Revoked"
                                        link.expired -> "Expired"
                                        else -> "Active"
                                    },
                                    color = if (link.revoked || link.expired) CrimsonAlert else EmeraldSync
                                )
                                if (link.passwordProtected) {
                                    StatusPill(text = "Password", color = AmberPending, icon = Icons.Filled.Lock)
                                }
                                StatusPill(
                                    text = formatExpiryMillis(link.expiresAtMillis),
                                    color = TextSecondary,
                                    icon = Icons.Filled.Schedule
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onToggleRevoke(link) }) {
                                    Icon(Icons.Filled.PublicOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (link.revoked) "Restore Access" else "Revoke Access")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = { onDeleteLink(link) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = CrimsonAlert)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
    }

    if (showResolverDialog) {
        ResolveShareLinkDialog(
            onDismiss = { showResolverDialog = false },
            onResolve = onResolveLink
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ResolveShareLinkDialog(
    onDismiss: () -> Unit,
    onResolve: (String, String, (Result<SharedLink>) -> Unit) -> Unit
) {
    var linkInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify & Unlock Share Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    label = { Text("Share URL or Link ID (lnk_...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password (if protected)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (resultText != null) {
                    Text(
                        text = resultText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) CrimsonAlert else EmeraldSync
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResolve(linkInput, passwordInput) { res ->
                        res.onSuccess { link ->
                            isError = false
                            resultText = "Unlocked: ${link.fileName} (${formatBytes(link.sizeBytes)}) · Opens: ${link.accessCount}"
                        }.onFailure { err ->
                            isError = true
                            resultText = err.localizedMessage ?: "Verification failed"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple)
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

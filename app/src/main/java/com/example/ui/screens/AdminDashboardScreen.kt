package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PlanTier
import com.example.ui.AdminDashboardState
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes
import com.example.util.formatTimestamp

@Composable
fun AdminDashboardScreen(
    adminState: AdminDashboardState,
    onRefresh: () -> Unit,
    onUpdateUserPlan: (targetUid: String, tier: PlanTier, status: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val totalUsers = adminState.users.size
    val totalConsumed = adminState.users.sumOf { it.usedBytes }
    val totalUploads = adminState.users.sumOf { it.totalUploads }
    val totalDownloads = adminState.users.sumOf { it.totalDownloads }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("admin_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text("Admin Console", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text("Protected by Firestore RBAC rules", style = MaterialTheme.typography.bodySmall, color = EmeraldSync)
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh telemetry", tint = LuminousLavender)
            }
        }

        if (adminState.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RoyalPurple)
            }
        } else if (adminState.error != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(adminState.error, color = CrimsonAlert)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Global KPI Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminStatCard(
                            label = "Users",
                            value = totalUsers.toString(),
                            color = LuminousLavender,
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatCard(
                            label = "Storage Used",
                            value = formatBytes(totalConsumed),
                            color = SkyInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminStatCard(
                            label = "Total Uploads",
                            value = totalUploads.toString(),
                            color = EmeraldSync,
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatCard(
                            label = "Total Exports",
                            value = totalDownloads.toString(),
                            color = AmberPending,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    SectionHeader(title = "Registered Users & Quotas")
                }

                items(adminState.users, key = { it.userId }) { user ->
                    var menuOpen by remember { mutableStateOf(false) }
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(
                                    text = "${formatBytes(user.usedBytes)} / ${formatBytes(user.quotaBytes)} · ${user.tierEnum.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuminousLavender
                                )
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { menuOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Text("Manage")
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    PlanTier.entries.forEach { tier ->
                                        DropdownMenuItem(
                                            text = { Text("Set ${tier.title}") },
                                            onClick = {
                                                menuOpen = false
                                                onUpdateUserPlan(user.userId, tier, user.accountStatus)
                                            }
                                        )
                                    }
                                    val nextStatus = if (user.accountStatus == "ACTIVE") "SUSPENDED" else "ACTIVE"
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (nextStatus == "SUSPENDED") "Suspend User" else "Reactivate User",
                                                color = if (nextStatus == "SUSPENDED") CrimsonAlert else EmeraldSync
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onUpdateUserPlan(user.userId, user.tierEnum, nextStatus)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(title = "Recent Audit & Telemetry")
                }

                items(adminState.logs.take(25), key = { it.id }) { log ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${log.actionEnum.label}: ${log.targetName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatBytes(log.bytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminousLavender
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${log.userEmail} · ${formatTimestamp(log.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.PlanTier
import com.example.model.UiState
import com.example.model.UserAccount
import com.example.ui.components.GlassCard
import com.example.ui.components.StorageGradientProgressBar
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.NeonOrchid
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.formatBytes

@Composable
fun ProfileScreen(
    accountState: UiState<UserAccount?>,
    isAdmin: Boolean,
    onOpenAdminDashboard: () -> Unit,
    onRecordPlanInquiry: (PlanTier) -> Unit,
    onSignOut: () -> Unit
) {
    val account = (accountState as? UiState.Success)?.data
    var yearlyBilling by remember { mutableStateOf(true) }
    var paymentInfoTier by remember { mutableStateOf<PlanTier?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowAccent = true
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(RoyalPurple, NeonOrchid))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!account?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = account.photoUrl,
                                contentDescription = "User avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (account?.displayName?.firstOrNull() ?: 'L').uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account?.displayName ?: "LavBox Member",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = account?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldSync.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Shield, contentDescription = null, tint = EmeraldSync, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${account?.tierEnum?.title ?: "Free Vault"} · ${account?.accountStatus ?: "ACTIVE"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSync
                                )
                            }
                        }
                    }
                }

                if (account != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    StorageGradientProgressBar(fraction = account.usageFraction)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatBytes(account.usedBytes)} of ${formatBytes(account.quotaBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuminousLavender
                        )
                        Text(
                            text = "${account.totalUploads} uploads · ${account.totalDownloads} exports",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Admin Console Entry (Only when verified admin; also enforced server-side by firestore.rules)
        if (isAdmin) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_console_card"),
                    onClick = onOpenAdminDashboard
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("LavBox Admin Console", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Server-verified telemetry, user quotas & audit logs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        Text("Open", style = MaterialTheme.typography.labelLarge, color = LuminousLavender)
                    }
                }
            }
        }

        // Subscription Plans
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ElectricViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage Plans", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !yearlyBilling,
                        onClick = { yearlyBilling = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Monthly", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = yearlyBilling,
                        onClick = { yearlyBilling = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Yearly", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanTier.entries.forEach { tier ->
                    val isCurrent = account?.tierEnum == tier
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowAccent = tier == PlanTier.PRO_1TB
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tier.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(tier.tagline, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text(
                                text = if (yearlyBilling) tier.yearlyPrice else tier.monthlyPrice,
                                style = MaterialTheme.typography.titleLarge,
                                color = LuminousLavender
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isCurrent) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSync, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Current Active Plan", style = MaterialTheme.typography.labelMedium, color = EmeraldSync)
                            }
                        } else if (tier != PlanTier.FREE) {
                            Button(
                                onClick = {
                                    onRecordPlanInquiry(tier)
                                    paymentInfoTier = tier
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("upgrade_${tier.name.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepAmethyst)
                            ) {
                                Text("Upgrade to ${tier.title}")
                            }
                        }
                    }
                }
            }
        }

        // Sign Out
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (paymentInfoTier != null) {
        val tier = paymentInfoTier!!
        AlertDialog(
            onDismissRequest = { paymentInfoTier = null },
            title = { Text("Verified Billing Required") },
            text = {
                Text(
                    "To protect quota integrity, ${tier.title} (${formatBytes(tier.quotaBytes)}) is never activated client-side without a verified Google Play Billing purchase token validated on your backend server.\n\nConnect your Play Console subscription SKU and server webhook to grant ${tier.title} automatically."
                )
            },
            confirmButton = {
                TextButton(onClick = { paymentInfoTier = null }) {
                    Text("Understood")
                }
            }
        )
    }
}

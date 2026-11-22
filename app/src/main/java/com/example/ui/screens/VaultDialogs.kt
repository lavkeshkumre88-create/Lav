package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.VaultFile
import com.example.ui.theme.AmberPending
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) onConfirm(folderName.trim())
                },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple),
                modifier = Modifier.testTag("confirm_create_folder_btn")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameFileDialog(
    file: VaultFile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(file) { mutableStateOf(file.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${if (file.folderFlag) "Folder" else "File"}") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onConfirm(newName.trim()) },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple),
                modifier = Modifier.testTag("confirm_rename_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    file: VaultFile,
    folders: List<VaultFile>,
    onDismiss: () -> Unit,
    onSelectDestination: (String) -> Unit
) {
    val destinations = remember(folders, file) {
        folders.filter { !it.trashed && it.folderFlag && it.id != file.id }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${file.name}\"") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassSurface,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDestination("") }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = AmberPending)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Vault Root", color = TextPrimary)
                        }
                    }
                }
                items(destinations, key = { it.id }) { folder ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GlassSurface,
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDestination(folder.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = AmberPending)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder.name, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateShareLinkDialog(
    file: VaultFile,
    onDismiss: () -> Unit,
    onCreateLink: (expiryHours: Int?, password: String) -> Unit
) {
    var selectedHours by remember { mutableStateOf<Int?>(168) } // 7 days default
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share \"${file.name}\"") },
        text = {
            Column {
                Text(
                    text = "Expiration",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        24 to "24h",
                        168 to "7d",
                        720 to "30d",
                        null to "Never"
                    ).forEach { (hours, label) ->
                        FilterChip(
                            selected = selectedHours == hours,
                            onClick = { selectedHours = hours },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Optional Link Password (SHA-256 hashed)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreateLink(selectedHours, password) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple),
                modifier = Modifier.testTag("confirm_share_link_btn")
            ) {
                Text("Generate Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileCategoryFilter
import com.example.model.FileKind
import com.example.model.FileSortOrder
import com.example.model.UiState
import com.example.model.VaultFile
import com.example.ui.components.GlassCard
import com.example.ui.components.VaultFileBadgeIcon
import com.example.ui.components.VaultFileRow
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VelvetSurface
import com.example.util.formatBytes

@Composable
fun FilesBrowserScreen(
    filesState: UiState<List<VaultFile>>,
    searchQuery: String,
    categoryFilter: FileCategoryFilter,
    sortOrder: FileSortOrder,
    currentFolderId: String,
    isGridView: Boolean,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FileCategoryFilter) -> Unit,
    onSortChange: (FileSortOrder) -> Unit,
    onOpenFolder: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onOpenFile: (VaultFile) -> Unit,
    onToggleFavorite: (VaultFile) -> Unit,
    onShareFile: (VaultFile) -> Unit,
    onRenameFile: (VaultFile) -> Unit,
    onMoveFile: (VaultFile) -> Unit,
    onDownloadFile: (VaultFile) -> Unit,
    onTrashOrRestoreFile: (VaultFile) -> Unit,
    onPermanentDeleteFile: (VaultFile) -> Unit,
    onEmptyTrash: (List<VaultFile>) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val allFiles = (filesState as? UiState.Success)?.data ?: emptyList()

    val currentFolder = remember(allFiles, currentFolderId) {
        allFiles.firstOrNull { it.id == currentFolderId && it.folderFlag }
    }

    val filteredFiles = remember(allFiles, searchQuery, categoryFilter, sortOrder, currentFolderId) {
        val base = when (categoryFilter) {
            FileCategoryFilter.TRASH -> allFiles.filter { it.trashed }
            FileCategoryFilter.FAVORITES -> allFiles.filter { !it.trashed && it.favorite }
            FileCategoryFilter.PHOTOS -> allFiles.filter { !it.trashed && it.kindEnum == FileKind.PHOTO }
            FileCategoryFilter.VIDEOS -> allFiles.filter { !it.trashed && it.kindEnum == FileKind.VIDEO }
            FileCategoryFilter.PDFS -> allFiles.filter { !it.trashed && it.kindEnum == FileKind.PDF }
            FileCategoryFilter.DOCUMENTS -> allFiles.filter {
                !it.trashed && (it.kindEnum == FileKind.DOCUMENT || it.kindEnum == FileKind.PDF)
            }
            FileCategoryFilter.ALL -> {
                if (searchQuery.isNotBlank()) {
                    allFiles.filter { !it.trashed }
                } else {
                    allFiles.filter { !it.trashed && it.parentFolderId == currentFolderId }
                }
            }
        }

        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim()
            base.filter { it.name.contains(q, ignoreCase = true) || it.mimeType.contains(q, ignoreCase = true) }
        }

        when (sortOrder) {
            FileSortOrder.NEWEST -> searched.sortedWith(
                compareByDescending<VaultFile> { it.folderFlag }
                    .thenByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
            )
            FileSortOrder.NAME_ASC -> searched.sortedWith(
                compareByDescending<VaultFile> { it.folderFlag }
                    .thenBy { it.name.lowercase() }
            )
            FileSortOrder.SIZE_DESC -> searched.sortedWith(
                compareByDescending<VaultFile> { it.folderFlag }
                    .thenByDescending { it.sizeBytes }
            )
            FileSortOrder.KIND -> searched.sortedWith(
                compareByDescending<VaultFile> { it.folderFlag }
                    .thenBy { it.kind }
            )
        }
    }

    val trashedList = remember(allFiles) { allFiles.filter { it.trashed } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Bar: Title + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (currentFolderId.isNotBlank() && categoryFilter == FileCategoryFilter.ALL) {
                    IconButton(
                        onClick = { onOpenFolder(currentFolder?.parentFolderId ?: "") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to parent folder",
                            tint = TextPrimary
                        )
                    }
                }
                Column {
                    Text(
                        text = currentFolder?.name ?: categoryFilter.label + " Vault",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${filteredFiles.size} item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (categoryFilter != FileCategoryFilter.TRASH) {
                    IconButton(
                        onClick = onCreateFolderClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("new_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = LuminousLavender
                        )
                    }
                }
                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_grid_button")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Toggle grid/list view",
                        tint = TextSecondary
                    )
                }
                Box {
                    IconButton(
                        onClick = { sortMenuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sort_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort files",
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        FileSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.label,
                                        color = if (order == sortOrder) LuminousLavender else TextPrimary
                                    )
                                },
                                onClick = {
                                    sortMenuOpen = false
                                    onSortChange(order)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search files, folders, MIME types...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RoyalPurple,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = VelvetSurface,
                unfocusedContainerColor = VelvetSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("files_search_input")
        )

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileCategoryFilter.entries.forEach { filter ->
                val selected = filter == categoryFilter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalPurple.copy(alpha = 0.28f),
                        selectedLabelColor = LuminousLavender,
                        containerColor = GlassSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = GlassBorder,
                        selectedBorderColor = RoyalPurple
                    ),
                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                )
            }
        }

        // Empty Trash Bar when viewing Trash
        if (categoryFilter == FileCategoryFilter.TRASH && trashedList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CrimsonAlert.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${trashedList.size} trashed file(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = { onEmptyTrash(trashedList) },
                        colors = ButtonDefaults.textButtonColors(contentColor = CrimsonAlert),
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Empty Trash")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (filesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RoyalPurple)
                }
            }
            is UiState.Error -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(filesState.message, color = CrimsonAlert)
                }
            }
            is UiState.Success -> {
                if (filteredFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = LuminousLavender,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching files" else "Nothing here yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create folders or upload files from the Upload tab.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 152.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("file_grid_${file.id}"),
                                onClick = {
                                    if (file.folderFlag) onOpenFolder(file.id) else onOpenFile(file)
                                }
                            ) {
                                VaultFileBadgeIcon(kind = file.kindEnum, size = 48.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (file.folderFlag) "Folder" else formatBytes(file.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuminousLavender
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            VaultFileRow(
                                file = file,
                                onOpen = {
                                    if (file.folderFlag) onOpenFolder(file.id) else onOpenFile(file)
                                },
                                onToggleFavorite = { onToggleFavorite(file) },
                                onShare = { onShareFile(file) },
                                onRename = { onRenameFile(file) },
                                onMove = { onMoveFile(file) },
                                onDownload = { onDownloadFile(file) },
                                onTrashOrRestore = { onTrashOrRestoreFile(file) },
                                onPermanentDelete = if (file.trashed) {
                                    { onPermanentDeleteFile(file) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

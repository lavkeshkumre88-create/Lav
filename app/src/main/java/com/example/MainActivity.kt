package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.LavBoxRepository
import com.example.data.NetworkMonitor
import com.example.data.VaultStorageEngine
import com.example.data.authStateFlow
import com.example.data.signOutFromLavBox
import com.example.model.FileCategoryFilter
import com.example.model.UiState
import com.example.model.VaultFile
import com.example.ui.LavBoxViewModel
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateFolderDialog
import com.example.ui.screens.CreateShareLinkDialog
import com.example.ui.screens.FilesBrowserScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.MoveToFolderDialog
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RenameFileDialog
import com.example.ui.screens.SharedLinksScreen
import com.example.ui.screens.UploadCenterScreen
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.LavBoxTheme
import com.example.ui.theme.LuminousLavender
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.VelvetSurface
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class LavBoxTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    UPLOAD("Upload", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    SHARED("Shared", Icons.Filled.Share, Icons.Outlined.Share),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LavBoxTheme {
                LavBoxRoot()
            }
        }
    }
}

@Composable
fun LavBoxRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: LavBoxViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        val repository = LavBoxRepository(
                            db = db,
                            storageEngine = VaultStorageEngine(app)
                        )
                        LavBoxViewModel(
                            repository = repository,
                            networkMonitor = NetworkMonitor(app),
                            currentUserId = uid,
                            currentUserEmail = user.email ?: "",
                            isEmailVerified = user.isEmailVerified
                        )
                    }
                }
            )
            LavBoxAuthenticatedShell(viewModel = viewModel)
        }
    }
}

@Composable
private fun LavBoxAuthenticatedShell(viewModel: LavBoxViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val accountState by viewModel.userAccountState.collectAsStateWithLifecycle()
    val filesState by viewModel.vaultFilesState.collectAsStateWithLifecycle()
    val sharedLinksState by viewModel.sharedLinksState.collectAsStateWithLifecycle()
    val uploadTasks by viewModel.uploadTasks.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val adminState by viewModel.adminState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(LavBoxTab.HOME) }
    var viewingFile by remember { mutableStateOf<VaultFile?>(null) }
    var showingAdmin by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VaultFile?>(null) }
    var moveTarget by remember { mutableStateOf<VaultFile?>(null) }
    var shareTarget by remember { mutableStateOf<VaultFile?>(null) }
    var exportTarget by remember { mutableStateOf<VaultFile?>(null) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
        onResult = { uri ->
            val target = exportTarget
            exportTarget = null
            if (uri != null && target != null) {
                viewModel.exportFileToDevice(target, uri)
            }
        }
    )

    val triggerDownload: (VaultFile) -> Unit = { file ->
        exportTarget = file
        exportDocumentLauncher.launch(file.name)
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    // Full-screen Media Viewer overlay
    if (viewingFile != null) {
        MediaViewerScreen(
            file = viewingFile!!,
            prepareFile = viewModel::prepareMediaPreviewFile,
            onDownload = triggerDownload,
            onShare = { shareTarget = it },
            onBack = { viewingFile = null }
        )
    } else if (showingAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ObsidianBlack, MidnightVoid)))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AdminDashboardScreen(
                adminState = adminState,
                onRefresh = viewModel::loadAdminDashboard,
                onUpdateUserPlan = viewModel::adminUpdateUser,
                onBack = { showingAdmin = false }
            )
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ObsidianBlack, MidnightVoid, DeepAmethyst.copy(alpha = 0.14f), ObsidianBlack)
                    )
                )
        ) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = VelvetSurface.copy(alpha = 0.96f),
                            tonalElevation = 8.dp
                        ) {
                            LavBoxTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = LuminousLavender,
                                        selectedTextColor = LuminousLavender,
                                        indicatorColor = RoyalPurple.copy(alpha = 0.28f),
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = VelvetSurface.copy(alpha = 0.96f)
                        ) {
                            LavBoxTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = LuminousLavender,
                                        selectedTextColor = LuminousLavender,
                                        indicatorColor = RoyalPurple.copy(alpha = 0.28f),
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 920.dp)
                        ) {
                            when (currentTab) {
                                LavBoxTab.HOME -> HomeDashboardScreen(
                                    accountState = accountState,
                                    filesState = filesState,
                                    sharedLinksState = sharedLinksState,
                                    uploadTasks = uploadTasks,
                                    onNavigateToUpload = { currentTab = LavBoxTab.UPLOAD },
                                    onNavigateToCategory = { filter ->
                                        viewModel.setCategoryFilter(filter)
                                        currentTab = LavBoxTab.FILES
                                    },
                                    onNavigateToShared = { currentTab = LavBoxTab.SHARED },
                                    onNavigateToUpgrade = { currentTab = LavBoxTab.PROFILE },
                                    onNavigateToProfile = { currentTab = LavBoxTab.PROFILE },
                                    onOpenFile = { file ->
                                        if (file.folderFlag) {
                                            viewModel.openFolder(file.id)
                                            currentTab = LavBoxTab.FILES
                                        } else {
                                            viewingFile = file
                                        }
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onShareFile = { shareTarget = it },
                                    onRenameFile = { renameTarget = it },
                                    onMoveFile = { moveTarget = it },
                                    onDownloadFile = triggerDownload,
                                    onTrashFile = viewModel::moveToTrash
                                )

                                LavBoxTab.FILES -> FilesBrowserScreen(
                                    filesState = filesState,
                                    searchQuery = searchQuery,
                                    categoryFilter = categoryFilter,
                                    sortOrder = sortOrder,
                                    currentFolderId = currentFolderId,
                                    isGridView = isGridView,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onFilterChange = viewModel::setCategoryFilter,
                                    onSortChange = viewModel::setSortOrder,
                                    onOpenFolder = viewModel::openFolder,
                                    onToggleGridView = viewModel::toggleGridView,
                                    onCreateFolderClick = { showCreateFolderDialog = true },
                                    onOpenFile = { viewingFile = it },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onShareFile = { shareTarget = it },
                                    onRenameFile = { renameTarget = it },
                                    onMoveFile = { moveTarget = it },
                                    onDownloadFile = triggerDownload,
                                    onTrashOrRestoreFile = { file ->
                                        if (file.trashed) viewModel.restoreFromTrash(file) else viewModel.moveToTrash(file)
                                    },
                                    onPermanentDeleteFile = viewModel::permanentlyDelete,
                                    onEmptyTrash = viewModel::emptyTrash
                                )

                                LavBoxTab.UPLOAD -> UploadCenterScreen(
                                    accountState = accountState,
                                    networkStatus = networkStatus,
                                    uploadTasks = uploadTasks,
                                    onEnqueueUploads = { uris, isBackup ->
                                        viewModel.enqueueUploads(uris, isBackup)
                                    },
                                    onPauseOrResume = viewModel::pauseOrResumeTask,
                                    onCancelTask = viewModel::cancelTask,
                                    onClearCompleted = viewModel::clearCompletedTasks,
                                    onUpdateBackupPreferences = viewModel::updateBackupPreferences
                                )

                                LavBoxTab.SHARED -> SharedLinksScreen(
                                    sharedLinksState = sharedLinksState,
                                    onToggleRevoke = viewModel::toggleRevokeShareLink,
                                    onDeleteLink = viewModel::deleteShareLink,
                                    onResolveLink = viewModel::resolveShareLink,
                                    onNotify = viewModel::showMessage
                                )

                                LavBoxTab.PROFILE -> ProfileScreen(
                                    accountState = accountState,
                                    isAdmin = isAdmin,
                                    onOpenAdminDashboard = { showingAdmin = true },
                                    onRecordPlanInquiry = viewModel::recordPlanInquiry,
                                    onSignOut = {
                                        signOutFromLavBox(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    renameTarget?.let { target ->
        RenameFileDialog(
            file = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameFile(target, newName)
            }
        )
    }

    moveTarget?.let { target ->
        val allFiles = (filesState as? UiState.Success)?.data ?: emptyList()
        MoveToFolderDialog(
            file = target,
            folders = allFiles,
            onDismiss = { moveTarget = null },
            onSelectDestination = { destId ->
                moveTarget = null
                viewModel.moveFile(target, destId)
            }
        )
    }

    shareTarget?.let { target ->
        CreateShareLinkDialog(
            file = target,
            onDismiss = { shareTarget = null },
            onCreateLink = { hours, pass ->
                shareTarget = null
                viewModel.createShareLink(target, hours, pass) {
                    currentTab = LavBoxTab.SHARED
                }
            }
        )
    }
}

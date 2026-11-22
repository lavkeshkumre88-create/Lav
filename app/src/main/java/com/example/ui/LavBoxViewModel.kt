package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LavBoxRepository
import com.example.data.NetworkMonitor
import com.example.data.NetworkStatus
import com.example.model.ActivityLog
import com.example.model.FileCategoryFilter
import com.example.model.FileKind
import com.example.model.FileSortOrder
import com.example.model.PlanTier
import com.example.model.SharedLink
import com.example.model.TransferStatus
import com.example.model.UiState
import com.example.model.UploadTaskState
import com.example.model.UserAccount
import com.example.model.VaultFile
import com.example.util.determineFileKind
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AdminDashboardState(
    val loading: Boolean = false,
    val users: List<UserAccount> = emptyList(),
    val logs: List<ActivityLog> = emptyList(),
    val error: String? = null
)

class LavBoxViewModel(
    private val repository: LavBoxRepository,
    private val networkMonitor: NetworkMonitor,
    val currentUserId: String,
    val currentUserEmail: String,
    private val isEmailVerified: Boolean
) : ViewModel() {

    private companion object {
        const val TAG = "LavBoxVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    val userAccountState: StateFlow<UiState<UserAccount?>> = repository.observeUserAccount(currentUserId)
        .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing user account", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load user profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val vaultFilesState: StateFlow<UiState<List<VaultFile>>> = repository.observeVaultFiles(currentUserId)
        .map<List<VaultFile>, UiState<List<VaultFile>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing vault files", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load vault files"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sharedLinksState: StateFlow<UiState<List<SharedLink>>> = repository.observeMySharedLinks(currentUserId)
        .map<List<SharedLink>, UiState<List<SharedLink>>> { links ->
            UiState.Success(links.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L })
        }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing shared links", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load shared links"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activityLogsState: StateFlow<List<ActivityLog>> = repository.observeMyActivityLogs(currentUserId)
        .map { logs -> logs.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }.take(30) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing activity logs", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminState = MutableStateFlow(AdminDashboardState())
    val adminState: StateFlow<AdminDashboardState> = _adminState.asStateFlow()

    private val _uploadTasks = MutableStateFlow<List<UploadTaskState>>(emptyList())
    val uploadTasks: StateFlow<List<UploadTaskState>> = _uploadTasks.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow(FileCategoryFilter.ALL)
    val categoryFilter: StateFlow<FileCategoryFilter> = _categoryFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(FileSortOrder.NEWEST)
    val sortOrder: StateFlow<FileSortOrder> = _sortOrder.asStateFlow()

    private val _currentFolderId = MutableStateFlow("")
    val currentFolderId: StateFlow<String> = _currentFolderId.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val pausedTasks = ConcurrentHashMap<String, Boolean>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                repository.ensureUserAccount(user).onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Unable to sync user profile"
                }
            }
            _isAdmin.value = repository.checkIsAdmin(currentUserId, currentUserEmail, isEmailVerified)
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(filter: FileCategoryFilter) {
        _categoryFilter.value = filter
        if (filter != FileCategoryFilter.ALL) {
            _currentFolderId.value = ""
        }
    }

    fun setSortOrder(order: FileSortOrder) {
        _sortOrder.value = order
    }

    fun openFolder(folderId: String) {
        _categoryFilter.value = FileCategoryFilter.ALL
        _currentFolderId.value = folderId
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun createFolder(name: String, parentFolderId: String = _currentFolderId.value) {
        viewModelScope.launch {
            repository.createFolder(currentUserId, name, parentFolderId)
                .onSuccess { _statusBanner.value = "Folder \"$name\" created" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create folder" }
        }
    }

    fun enqueueUploads(
        uris: List<Uri>,
        isBackup: Boolean = false,
        parentFolderId: String = _currentFolderId.value
    ) {
        if (uris.isEmpty()) return
        val account = (userAccountState.value as? UiState.Success)?.data
        if (account == null) {
            _statusBanner.value = "Cloud profile still loading. Please try again in a moment."
            return
        }

        val net = networkStatus.value
        val blockedByWifi = isBackup && account.wifiOnlyBackup && !net.unmeteredWifi

        uris.forEach { uri ->
            val meta = repository.inspectPickedUri(uri)
            if (meta == null) {
                _statusBanner.value = "Could not inspect selected file"
                return@forEach
            }
            val kind = determineFileKind(meta.mimeType, meta.displayName)
            val taskId = "tsk_${UUID.randomUUID().toString().replace("-", "").take(12)}"

            if (meta.sizeBytes > account.remainingBytes) {
                val failedTask = UploadTaskState(
                    taskId = taskId,
                    fileName = meta.displayName,
                    mimeType = meta.mimeType,
                    kind = kind,
                    totalBytes = meta.sizeBytes,
                    status = TransferStatus.FAILED,
                    backupTask = isBackup,
                    parentFolderId = parentFolderId,
                    sourceUriString = uri.toString(),
                    errorMessage = "Insufficient cloud quota remaining"
                )
                _uploadTasks.update { listOf(failedTask) + it }
                _statusBanner.value = "Quota limit reached for ${meta.displayName}"
                return@forEach
            }

            val initialStatus = if (!net.connected || blockedByWifi) {
                TransferStatus.WAITING_NETWORK
            } else {
                TransferStatus.UPLOADING
            }

            val task = UploadTaskState(
                taskId = taskId,
                fileName = meta.displayName,
                mimeType = meta.mimeType,
                kind = kind,
                totalBytes = meta.sizeBytes,
                status = initialStatus,
                backupTask = isBackup,
                parentFolderId = parentFolderId,
                sourceUriString = uri.toString()
            )
            _uploadTasks.update { listOf(task) + it }
            if (initialStatus == TransferStatus.UPLOADING) {
                startUploadJob(task, uri, account)
            } else {
                _statusBanner.value = if (blockedByWifi) {
                    "Backup queued: waiting for Wi-Fi connection"
                } else {
                    "Upload queued: waiting for network"
                }
            }
        }
    }

    private fun startUploadJob(task: UploadTaskState, uri: Uri, account: UserAccount) {
        pausedTasks[task.taskId] = false
        val job = viewModelScope.launch {
            updateTask(task.taskId) { it.copy(status = TransferStatus.UPLOADING, errorMessage = null) }
            val result = repository.uploadFileFromUri(
                userId = currentUserId,
                userEmail = currentUserEmail,
                account = account,
                uri = uri,
                fileName = task.fileName,
                mimeType = task.mimeType,
                kind = task.kind,
                expectedBytes = task.totalBytes,
                parentFolderId = task.parentFolderId,
                isBackup = task.backupTask,
                isPaused = { pausedTasks[task.taskId] == true },
                onProgress = { copied, total ->
                    updateTask(task.taskId) { current ->
                        current.copy(
                            transferredBytes = copied,
                            totalBytes = total.coerceAtLeast(1L),
                            status = if (pausedTasks[task.taskId] == true) {
                                TransferStatus.PAUSED
                            } else {
                                TransferStatus.UPLOADING
                            }
                        )
                    }
                }
            )
            result.onSuccess { uploaded ->
                updateTask(task.taskId) {
                    it.copy(
                        transferredBytes = uploaded.sizeBytes,
                        totalBytes = uploaded.sizeBytes.coerceAtLeast(1L),
                        status = TransferStatus.COMPLETED
                    )
                }
                _statusBanner.value = "${uploaded.name} uploaded to LavBox"
            }.onFailure { err ->
                if (err is CancellationException) {
                    updateTask(task.taskId) { it.copy(status = TransferStatus.CANCELLED) }
                } else {
                    updateTask(task.taskId) {
                        it.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = err.localizedMessage ?: "Upload interrupted"
                        )
                    }
                    _statusBanner.value = err.localizedMessage ?: "Upload failed"
                }
            }
            activeJobs.remove(task.taskId)
        }
        activeJobs[task.taskId] = job
    }

    fun pauseOrResumeTask(taskId: String) {
        val current = _uploadTasks.value.firstOrNull { it.taskId == taskId } ?: return
        when (current.status) {
            TransferStatus.UPLOADING -> {
                pausedTasks[taskId] = true
                updateTask(taskId) { it.copy(status = TransferStatus.PAUSED) }
            }
            TransferStatus.PAUSED -> {
                pausedTasks[taskId] = false
                updateTask(taskId) { it.copy(status = TransferStatus.UPLOADING) }
            }
            TransferStatus.WAITING_NETWORK, TransferStatus.FAILED -> {
                val account = (userAccountState.value as? UiState.Success)?.data ?: return
                val uri = Uri.parse(current.sourceUriString)
                startUploadJob(current, uri, account)
            }
            else -> Unit
        }
    }

    fun cancelTask(taskId: String) {
        pausedTasks.remove(taskId)
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) { it.copy(status = TransferStatus.CANCELLED) }
    }

    fun clearCompletedTasks() {
        _uploadTasks.update { list ->
            list.filterNot {
                it.status == TransferStatus.COMPLETED || it.status == TransferStatus.CANCELLED
            }
        }
    }

    private fun updateTask(taskId: String, transform: (UploadTaskState) -> UploadTaskState) {
        _uploadTasks.update { list ->
            list.map { if (it.taskId == taskId) transform(it) else it }
        }
    }

    fun renameFile(file: VaultFile, newName: String) {
        viewModelScope.launch {
            repository.renameFile(currentUserId, file.id, newName)
                .onSuccess { _statusBanner.value = "Renamed to $newName" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Rename failed" }
        }
    }

    fun moveFile(file: VaultFile, targetFolderId: String) {
        viewModelScope.launch {
            repository.moveFile(currentUserId, file.id, targetFolderId)
                .onSuccess { _statusBanner.value = "Moved ${file.name}" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Move failed" }
        }
    }

    fun toggleFavorite(file: VaultFile) {
        viewModelScope.launch {
            repository.toggleFavorite(currentUserId, file.id, !file.favorite)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update favorite" }
        }
    }

    fun moveToTrash(file: VaultFile) {
        viewModelScope.launch {
            repository.setTrashed(currentUserId, currentUserEmail, file, true)
                .onSuccess { _statusBanner.value = "${file.name} moved to Trash" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to trash file" }
        }
    }

    fun restoreFromTrash(file: VaultFile) {
        viewModelScope.launch {
            repository.setTrashed(currentUserId, currentUserEmail, file, false)
                .onSuccess { _statusBanner.value = "${file.name} restored" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Restore failed" }
        }
    }

    fun permanentlyDelete(file: VaultFile) {
        viewModelScope.launch {
            repository.permanentlyDeleteFile(currentUserId, file)
                .onSuccess { _statusBanner.value = "Permanently deleted ${file.name}" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun emptyTrash(trashedFiles: List<VaultFile>) {
        if (trashedFiles.isEmpty()) return
        viewModelScope.launch {
            repository.emptyTrash(currentUserId, trashedFiles)
                .onSuccess { _statusBanner.value = "Trash emptied & storage reclaimed" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to empty trash" }
        }
    }

    fun createShareLink(
        file: VaultFile,
        expiryHours: Int?,
        password: String,
        onCreated: (SharedLink) -> Unit
    ) {
        viewModelScope.launch {
            val expiresAt = if (expiryHours != null && expiryHours > 0) {
                System.currentTimeMillis() + expiryHours * 3600_000L
            } else {
                0L
            }
            repository.createShareLink(currentUserId, currentUserEmail, file, expiresAt, password)
                .onSuccess { link ->
                    _statusBanner.value = "Share link generated for ${file.name}"
                    onCreated(link)
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create share link" }
        }
    }

    fun toggleRevokeShareLink(link: SharedLink) {
        viewModelScope.launch {
            repository.toggleRevokeShareLink(link.id, !link.revoked)
                .onSuccess {
                    _statusBanner.value = if (!link.revoked) "Access revoked" else "Link reactivated"
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update link" }
        }
    }

    fun deleteShareLink(link: SharedLink) {
        viewModelScope.launch {
            repository.deleteShareLink(link.id)
                .onSuccess { _statusBanner.value = "Share link removed" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete share link" }
        }
    }

    fun resolveShareLink(
        linkIdOrUrl: String,
        passwordAttempt: String,
        onResult: (Result<SharedLink>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.resolveShareLink(linkIdOrUrl, passwordAttempt)
            onResult(res)
        }
    }

    fun prepareMediaPreviewFile(file: VaultFile, onReady: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val res = repository.ensureLocalFileForPreviewOrExport(currentUserId, file)
            onReady(res)
        }
    }

    fun exportFileToDevice(file: VaultFile, destinationUri: Uri) {
        viewModelScope.launch {
            repository.exportFileToUri(currentUserId, currentUserEmail, file, destinationUri)
                .onSuccess { _statusBanner.value = "Saved ${file.name} to device" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Export failed" }
        }
    }

    fun updateBackupPreferences(autoPhotos: Boolean, autoVideos: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            repository.updateBackupSettings(currentUserId, autoPhotos, autoVideos, wifiOnly)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update backup preferences" }
        }
    }

    fun recordPlanInquiry(tier: PlanTier) {
        viewModelScope.launch {
            repository.logPlanInquiry(currentUserId, currentUserEmail, tier)
        }
    }

    // --- Admin Actions ---
    fun loadAdminDashboard() {
        viewModelScope.launch {
            _adminState.update { it.copy(loading = true, error = null) }
            val usersRes = repository.fetchAllUsersForAdmin()
            val logsRes = repository.fetchGlobalActivityLogsForAdmin()
            if (usersRes.isSuccess && logsRes.isSuccess) {
                _adminState.value = AdminDashboardState(
                    loading = false,
                    users = usersRes.getOrDefault(emptyList()),
                    logs = logsRes.getOrDefault(emptyList()),
                    error = null
                )
            } else {
                val err = usersRes.exceptionOrNull() ?: logsRes.exceptionOrNull()
                _adminState.update {
                    it.copy(
                        loading = false,
                        error = err?.localizedMessage ?: "Admin authorization required"
                    )
                }
            }
        }
    }

    fun adminUpdateUser(targetUserId: String, tier: PlanTier, status: String) {
        viewModelScope.launch {
            repository.adminUpdateUserSubscriptionAndStatus(targetUserId, tier, status)
                .onSuccess {
                    _statusBanner.value = "Updated user account to ${tier.title} ($status)"
                    loadAdminDashboard()
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Admin update denied"
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeJobs.values.forEach { it.cancel() }
        networkMonitor.release()
    }
}

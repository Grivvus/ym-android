package sstu.grivvus.ym.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.ArchiveOperationState
import sstu.grivvus.ym.data.BackupCreationOptions
import sstu.grivvus.ym.data.BackupOperationStatus
import sstu.grivvus.ym.data.BackupRestoreRepository
import sstu.grivvus.ym.data.DownloadedBackupArchive
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.RestoreOperationStatus
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.download.TrackDownloadEvent
import sstu.grivvus.ym.data.download.TrackDownloadManager
import sstu.grivvus.ym.data.download.TrackDownloadOperation
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.ConflictApiException
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull

data class ArchiveStatusUi(
    val operationId: String,
    val idLabel: UiText,
    val title: UiText,
    val pollingMessage: UiText,
    val isFinished: Boolean,
    val isFailed: Boolean,
    val sizeBytes: Long? = null,
    val errorMessage: UiText? = null,
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSuperuser: Boolean = false,
    val tracks: List<LibraryTrackItemUi> = emptyList(),
    val selectedTrackIds: Set<Long> = emptySet(),
    val pendingDeleteTrackIds: Set<Long> = emptySet(),
    val downloadingTrackIds: Set<Long> = emptySet(),
    val isTrackMutating: Boolean = false,
    val includeImages: Boolean = true,
    val includeTranscodedTracks: Boolean = true,
    val isCreatingBackup: Boolean = false,
    val isDownloadingBackup: Boolean = false,
    val isSavingBackup: Boolean = false,
    val isStartingRestore: Boolean = false,
    val backupStatus: ArchiveStatusUi? = null,
    val restoreStatus: ArchiveStatusUi? = null,
    val errorMessage: UiText? = null,
    val infoMessage: UiText? = null,
) {
    val isSelectionMode: Boolean
        get() = selectedTrackIds.isNotEmpty()
}

sealed interface LibraryScreenEvent {
    data class RequestBackupSaveLocation(val suggestedFileName: String) : LibraryScreenEvent
    data class NavigateToAlbum(val albumId: Long) : LibraryScreenEvent
    data class NavigateToArtist(val artistId: Long) : LibraryScreenEvent
    data class ShowDownloadResult(
        val message: UiText,
        val kind: LibraryDownloadResultKind,
    ) : LibraryScreenEvent
}

enum class LibraryDownloadResultKind {
    DOWNLOADED,
    LOCAL_COPY_DELETED,
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val backupRestoreRepository: BackupRestoreRepository,
    private val musicRepository: MusicRepository,
    private val trackDownloadManager: TrackDownloadManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    private val _events = MutableSharedFlow<LibraryScreenEvent>()
    private var pendingBackupArchive: DownloadedBackupArchive? = null
    private var backupPollingJob: Job? = null
    private var restorePollingJob: Job? = null

    val uiState = _uiState.asStateFlow()
    val events: SharedFlow<LibraryScreenEvent> = _events.asSharedFlow()

    init {
        observeTrackDownloadState()
        observeTrackDownloadEvents()
        observeCurrentUser()
        refreshCurrentUser()
        refresh(initialLoad = true)
    }

    fun changeIncludeImages(value: Boolean) {
        _uiState.update { it.copy(includeImages = value) }
    }

    fun changeIncludeTranscodedTracks(value: Boolean) {
        _uiState.update { it.copy(includeTranscodedTracks = value) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun refresh(initialLoad: Boolean = false) {
        viewModelScope.launch {
            if (initialLoad) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            }
            try {
                applyLibraryData(musicRepository.loadLibrary(refreshFromNetwork = true))
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                val fallbackData =
                    runCatching { musicRepository.loadLibrary(refreshFromNetwork = false) }.getOrNull()
                if (fallbackData != null) {
                    applyLibraryData(fallbackData)
                }
                error.logHandledException("LibraryViewModel.refresh")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun reloadFromLocal() {
        viewModelScope.launch {
            try {
                applyLibraryData(musicRepository.loadLibrary(refreshFromNetwork = false))
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.reloadFromLocal")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            }
        }
    }

    fun onTrackClick(trackId: Long) {
        if (_uiState.value.isSelectionMode) {
            toggleTrackSelection(trackId)
            return
        }
        val track = _uiState.value.tracks.firstOrNull { item -> item.id == trackId } ?: return
        val albumId = track.albumId
        if (albumId == null) {
            _uiState.update {
                it.copy(
                    errorMessage = UiText.StringResource(
                        R.string.library_error_album_unavailable_for_track,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _events.emit(LibraryScreenEvent.NavigateToAlbum(albumId))
        }
    }

    fun onTrackLongPress(trackId: Long) {
        toggleTrackSelection(trackId)
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTrackIds = emptySet()) }
    }

    fun requestDeleteSelected() {
        val selectedTrackIds = _uiState.value.selectedTrackIds
        if (selectedTrackIds.isEmpty()) {
            return
        }
        _uiState.update { it.copy(pendingDeleteTrackIds = selectedTrackIds) }
    }

    fun requestDeleteTrack(trackId: Long) {
        _uiState.update { it.copy(pendingDeleteTrackIds = setOf(trackId)) }
    }

    fun downloadTrack(trackId: Long) {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        trackDownloadManager.downloadTrack(trackId)
    }

    fun deleteLocalTrackCopy(trackId: Long) {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        trackDownloadManager.deleteLocalCopy(trackId)
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(pendingDeleteTrackIds = emptySet()) }
    }

    fun deletePendingTracks() {
        val pendingDeleteTrackIds = _uiState.value.pendingDeleteTrackIds
        if (pendingDeleteTrackIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTrackMutating = true, errorMessage = null, infoMessage = null) }
            try {
                trackDownloadManager.cancelDownloads(pendingDeleteTrackIds)
                val updatedLibrary = musicRepository.deleteTracks(pendingDeleteTrackIds)
                applyLibraryData(updatedLibrary)
                _uiState.update { state ->
                    state.copy(
                        infoMessage = UiText.PluralResource(
                            R.plurals.library_deleted_tracks_info,
                            pendingDeleteTrackIds.size,
                        ),
                        pendingDeleteTrackIds = emptySet(),
                        selectedTrackIds = emptySet(),
                    )
                }
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.deletePendingTracks")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                _uiState.update { it.copy(isTrackMutating = false) }
            }
        }
    }

    fun openArtist(trackId: Long) {
        val track = _uiState.value.tracks.firstOrNull { item -> item.id == trackId } ?: return
        viewModelScope.launch {
            _events.emit(LibraryScreenEvent.NavigateToArtist(track.artistId))
        }
    }

    fun openAlbum(trackId: Long) {
        val track = _uiState.value.tracks.firstOrNull { item -> item.id == trackId } ?: return
        val albumId = track.albumId
        if (albumId == null) {
            _uiState.update {
                it.copy(
                    errorMessage = UiText.StringResource(
                        R.string.library_error_album_unavailable_for_track,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _events.emit(LibraryScreenEvent.NavigateToAlbum(albumId))
        }
    }

    fun createBackup() {
        if (!_uiState.value.isSuperuser ||
            _uiState.value.isCreatingBackup ||
            _uiState.value.isDownloadingBackup ||
            _uiState.value.isSavingBackup ||
            _uiState.value.isStartingRestore ||
            hasRunningBackup() ||
            hasRunningRestore()
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingBackup = true, errorMessage = null, infoMessage = null) }
            try {
                pendingBackupArchive?.let { archive ->
                    backupRestoreRepository.discardBackupArchive(archive)
                }
                pendingBackupArchive = null
                val status = backupRestoreRepository.startBackup(
                    options = BackupCreationOptions(
                        includeImages = _uiState.value.includeImages,
                        includeTranscodedTracks = _uiState.value.includeTranscodedTracks,
                    ),
                )
                _uiState.update { state ->
                    state.copy(backupStatus = status.toUi())
                }
                startBackupPolling(status.backupId)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.createBackup")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                _uiState.update { it.copy(isCreatingBackup = false) }
            }
        }
    }

    fun saveBackupTo(destinationUri: Uri?) {
        val archive = pendingBackupArchive ?: return
        if (destinationUri == null) {
            viewModelScope.launch {
                backupRestoreRepository.discardBackupArchive(archive)
                pendingBackupArchive = null
                _uiState.update { it.copy(backupStatus = null) }
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingBackup = true, errorMessage = null, infoMessage = null) }
            try {
                backupRestoreRepository.saveBackupArchive(archive, destinationUri)
                _uiState.update {
                    it.copy(
                        backupStatus = null,
                        infoMessage = UiText.StringResource(
                            R.string.library_info_backup_saved_successfully,
                        ),
                    )
                }
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.saveBackupTo")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                backupRestoreRepository.discardBackupArchive(archive)
                pendingBackupArchive = null
                _uiState.update { it.copy(isSavingBackup = false) }
            }
        }
    }

    fun restoreFromArchive(sourceUri: Uri?) {
        if (sourceUri == null ||
            !_uiState.value.isSuperuser ||
            _uiState.value.isCreatingBackup ||
            _uiState.value.isDownloadingBackup ||
            _uiState.value.isSavingBackup ||
            _uiState.value.isStartingRestore ||
            hasRunningBackup() ||
            hasRunningRestore()
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingRestore = true, errorMessage = null, infoMessage = null) }
            try {
                val restoreId = backupRestoreRepository.startRestore(sourceUri)
                _uiState.update { state ->
                    state.copy(
                        restoreStatus = RestoreOperationStatus(
                            restoreId = restoreId,
                            state = ArchiveOperationState.PENDING,
                        ).toUi(),
                    )
                }
                startRestorePolling(restoreId)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.restoreFromArchive")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                _uiState.update { it.copy(isStartingRestore = false) }
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collectLatest { currentUser ->
                _uiState.update { state ->
                    state.copy(
                        isSuperuser = currentUser?.isSuperuser == true,
                    )
                }
            }
        }
    }

    private fun observeTrackDownloadState() {
        viewModelScope.launch {
            trackDownloadManager.downloadingTrackIds.collectLatest { downloadingTrackIds ->
                _uiState.update { state ->
                    state.copy(downloadingTrackIds = downloadingTrackIds)
                }
            }
        }
    }

    private fun observeTrackDownloadEvents() {
        viewModelScope.launch {
            trackDownloadManager.events.collectLatest { event ->
                when (event) {
                    is TrackDownloadEvent.Downloaded -> {
                        refreshLocalStateAfterTrackDownloadEvent(
                            message = UiText.StringResource(R.string.library_info_track_downloaded),
                            kind = LibraryDownloadResultKind.DOWNLOADED,
                        )
                    }

                    is TrackDownloadEvent.LocalCopyDeleted -> {
                        refreshLocalStateAfterTrackDownloadEvent(
                            message = UiText.StringResource(
                                R.string.library_info_local_track_copy_deleted,
                            ),
                            kind = LibraryDownloadResultKind.LOCAL_COPY_DELETED,
                        )
                    }

                    is TrackDownloadEvent.Failed -> {
                        event.error.logHandledException("LibraryViewModel.trackDownload")
                        _uiState.update { state ->
                            state.copy(
                                errorMessage = event.toReadableMessage(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun refreshCurrentUser() {
        viewModelScope.launch {
            try {
                userRepository.updateLocalUserFromNetwork()
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("LibraryViewModel.refreshCurrentUser")
            }
        }
    }

    private fun startBackupPolling(backupId: String) {
        backupPollingJob?.cancel()
        backupPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val status = backupRestoreRepository.getBackupStatus(backupId)
                    _uiState.update { state ->
                        state.copy(
                            backupStatus = status.toUi(),
                            errorMessage = if (status.state == ArchiveOperationState.ERROR) {
                                status.errorMessage.asUiTextOrNull()
                                    ?: UiText.StringResource(R.string.library_error_backup_failed)
                            } else {
                                state.errorMessage
                            },
                        )
                    }
                    if (status.isTerminal) {
                        if (status.state == ArchiveOperationState.FINISHED) {
                            downloadFinishedBackup(status.backupId)
                        }
                        break
                    }
                } catch (_: SessionExpiredException) {
                    return@launch
                } catch (error: Exception) {
                    error.logHandledException("LibraryViewModel.startBackupPolling")
                    _uiState.update { state ->
                        state.copy(errorMessage = error.toReadableMessage())
                    }
                    break
                }
                delay(ARCHIVE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun downloadFinishedBackup(backupId: String) {
        _uiState.update { it.copy(isDownloadingBackup = true, errorMessage = null, infoMessage = null) }
        try {
            val archive = backupRestoreRepository.downloadBackupArchive(backupId)
            pendingBackupArchive = archive
            _events.emit(
                LibraryScreenEvent.RequestBackupSaveLocation(
                    suggestedFileName = archive.suggestedFileName,
                ),
            )
        } catch (_: SessionExpiredException) {
            throw SessionExpiredException()
        } catch (error: Exception) {
            error.logHandledException("LibraryViewModel.downloadFinishedBackup")
            _uiState.update { state ->
                state.copy(errorMessage = error.toReadableMessage())
            }
        } finally {
            _uiState.update { it.copy(isDownloadingBackup = false) }
        }
    }

    private fun startRestorePolling(restoreId: String) {
        restorePollingJob?.cancel()
        restorePollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val status = backupRestoreRepository.getRestoreStatus(restoreId)
                    _uiState.update { state ->
                        state.copy(
                            restoreStatus = status.toUi(),
                            errorMessage = if (status.state == ArchiveOperationState.ERROR) {
                                status.errorMessage.asUiTextOrNull()
                                    ?: UiText.StringResource(R.string.library_error_restore_failed)
                            } else {
                                state.errorMessage
                            },
                        )
                    }
                    if (status.isTerminal) {
                        if (status.state == ArchiveOperationState.FINISHED) {
                            refreshLocalStateAfterRestore()
                        }
                        break
                    }
                } catch (_: SessionExpiredException) {
                    return@launch
                } catch (error: Exception) {
                    error.logHandledException("LibraryViewModel.startRestorePolling")
                    _uiState.update { state ->
                        state.copy(errorMessage = error.toReadableMessage())
                    }
                    break
                }
                delay(ARCHIVE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshLocalStateAfterRestore() {
        try {
            backupRestoreRepository.refreshLocalStateAfterRestore()
            applyLibraryData(musicRepository.loadLibrary(refreshFromNetwork = false))
            _uiState.update { state ->
                state.copy(
                    infoMessage = UiText.StringResource(
                        R.string.library_info_restore_finished_local_data_refreshed,
                    ),
                )
            }
        } catch (_: SessionExpiredException) {
            throw SessionExpiredException()
        } catch (error: Exception) {
            error.logHandledException("LibraryViewModel.refreshLocalStateAfterRestore")
            _uiState.update { state ->
                state.copy(
                    errorMessage = UiText.StringResource(
                        R.string.library_error_restore_finished_local_sync_failed,
                        listOf(error.toReadableMessage()),
                    ),
                )
            }
        }
    }

    private suspend fun refreshLocalStateAfterTrackDownloadEvent(
        message: UiText,
        kind: LibraryDownloadResultKind,
    ) {
        try {
            applyLibraryData(musicRepository.loadLibrary(refreshFromNetwork = false))
            _events.emit(LibraryScreenEvent.ShowDownloadResult(message, kind))
        } catch (_: SessionExpiredException) {
            return
        } catch (error: Exception) {
            error.logHandledException("LibraryViewModel.refreshLocalStateAfterTrackDownloadEvent")
            _uiState.update { state ->
                state.copy(errorMessage = error.toReadableMessage())
            }
        }
    }

    private fun hasRunningRestore(): Boolean {
        val restoreStatus = _uiState.value.restoreStatus ?: return false
        return !restoreStatus.isFinished && !restoreStatus.isFailed
    }

    private fun hasRunningBackup(): Boolean {
        val backupStatus = _uiState.value.backupStatus ?: return false
        return !backupStatus.isFinished && !backupStatus.isFailed
    }

    private fun toggleTrackSelection(trackId: Long) {
        _uiState.update { state ->
            state.copy(
                selectedTrackIds = if (trackId in state.selectedTrackIds) {
                    state.selectedTrackIds - trackId
                } else {
                    state.selectedTrackIds + trackId
                },
            )
        }
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        val artistsById = data.artists.associateBy { artist -> artist.remoteId }
        val tracks = data.libraryTracks.map { track ->
            track.toLibraryTrackItemUi(artistsById)
        }
        val trackIds = tracks.mapTo(linkedSetOf()) { track -> track.id }
        _uiState.update { state ->
            state.copy(
                tracks = tracks,
                selectedTrackIds = state.selectedTrackIds.filterTo(linkedSetOf()) { it in trackIds },
                pendingDeleteTrackIds = state.pendingDeleteTrackIds.filterTo(linkedSetOf()) { it in trackIds },
                errorMessage = null,
            )
        }
    }

    private fun BackupOperationStatus.toUi(): ArchiveStatusUi {
        return when (state) {
            ArchiveOperationState.PENDING -> ArchiveStatusUi(
                operationId = backupId,
                idLabel = UiText.StringResource(R.string.library_backup_id, listOf(backupId)),
                title = UiText.StringResource(R.string.library_backup_status_queued),
                pollingMessage = UiText.StringResource(R.string.library_backup_polling_status),
                isFinished = false,
                isFailed = false,
                sizeBytes = sizeBytes,
            )

            ArchiveOperationState.STARTED -> ArchiveStatusUi(
                operationId = backupId,
                idLabel = UiText.StringResource(R.string.library_backup_id, listOf(backupId)),
                title = UiText.StringResource(R.string.library_backup_status_in_progress),
                pollingMessage = UiText.StringResource(R.string.library_backup_polling_status),
                isFinished = false,
                isFailed = false,
                sizeBytes = sizeBytes,
            )

            ArchiveOperationState.FINISHED -> ArchiveStatusUi(
                operationId = backupId,
                idLabel = UiText.StringResource(R.string.library_backup_id, listOf(backupId)),
                title = UiText.StringResource(R.string.library_backup_status_finished),
                pollingMessage = UiText.StringResource(R.string.library_backup_download_status),
                isFinished = true,
                isFailed = false,
                sizeBytes = sizeBytes,
            )

            ArchiveOperationState.ERROR -> ArchiveStatusUi(
                operationId = backupId,
                idLabel = UiText.StringResource(R.string.library_backup_id, listOf(backupId)),
                title = UiText.StringResource(R.string.library_backup_status_failed),
                pollingMessage = UiText.StringResource(R.string.library_backup_polling_status),
                isFinished = false,
                isFailed = true,
                sizeBytes = sizeBytes,
                errorMessage = errorMessage.asUiTextOrNull(),
            )
        }
    }

    private fun RestoreOperationStatus.toUi(): ArchiveStatusUi {
        return when (state) {
            ArchiveOperationState.PENDING -> ArchiveStatusUi(
                operationId = restoreId,
                idLabel = UiText.StringResource(R.string.library_restore_id, listOf(restoreId)),
                title = UiText.StringResource(R.string.library_restore_status_queued),
                pollingMessage = UiText.StringResource(R.string.library_restore_polling_status),
                isFinished = false,
                isFailed = false,
            )

            ArchiveOperationState.STARTED -> ArchiveStatusUi(
                operationId = restoreId,
                idLabel = UiText.StringResource(R.string.library_restore_id, listOf(restoreId)),
                title = UiText.StringResource(R.string.library_restore_status_in_progress),
                pollingMessage = UiText.StringResource(R.string.library_restore_polling_status),
                isFinished = false,
                isFailed = false,
            )

            ArchiveOperationState.FINISHED -> ArchiveStatusUi(
                operationId = restoreId,
                idLabel = UiText.StringResource(R.string.library_restore_id, listOf(restoreId)),
                title = UiText.StringResource(R.string.library_restore_status_finished),
                pollingMessage = UiText.StringResource(R.string.library_restore_polling_status),
                isFinished = true,
                isFailed = false,
            )

            ArchiveOperationState.ERROR -> ArchiveStatusUi(
                operationId = restoreId,
                idLabel = UiText.StringResource(R.string.library_restore_id, listOf(restoreId)),
                title = UiText.StringResource(R.string.library_restore_status_failed),
                pollingMessage = UiText.StringResource(R.string.library_restore_polling_status),
                isFinished = false,
                isFailed = true,
                errorMessage = errorMessage.asUiTextOrNull(),
            )
        }
    }

    private fun Throwable.toReadableMessage(): UiText {
        return when (this) {
            is UnauthorizedApiException ->
                UiText.StringResource(R.string.common_error_authentication_required)

            is ConflictApiException ->
                UiText.StringResource(R.string.library_error_archive_operation_already_running)

            is ClientApiException -> when (statusCode) {
                403 -> UiText.StringResource(R.string.common_error_superuser_access_required)
                409 -> UiText.StringResource(R.string.library_error_archive_operation_already_running)
                else -> message.asUiTextOrNull()
                    ?: UiText.StringResource(R.string.common_error_request_failed)
            }

            is ApiException -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_server_request_failed)

            is IOException -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_network_request_failed)

            else -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_unexpected)
        }
    }

    private fun TrackDownloadEvent.Failed.toReadableMessage(): UiText {
        val cause = error.toReadableMessage()
        return when (operation) {
            TrackDownloadOperation.DOWNLOAD ->
                UiText.StringResource(R.string.library_error_track_download_failed, listOf(cause))

            TrackDownloadOperation.DELETE_LOCAL_COPY ->
                UiText.StringResource(R.string.library_error_local_track_copy_delete_failed, listOf(cause))
        }
    }

    override fun onCleared() {
        backupPollingJob?.cancel()
        restorePollingJob?.cancel()
        pendingBackupArchive?.file?.delete()
        pendingBackupArchive = null
        super.onCleared()
    }

    private companion object {
        private const val ARCHIVE_POLL_INTERVAL_MS = 2_000L
    }
}

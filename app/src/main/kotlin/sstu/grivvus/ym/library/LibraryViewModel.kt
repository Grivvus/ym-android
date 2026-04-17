package sstu.grivvus.ym.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import sstu.grivvus.ym.data.BackupCreationOptions
import sstu.grivvus.ym.data.BackupRestoreRepository
import sstu.grivvus.ym.data.DownloadedBackupArchive
import sstu.grivvus.ym.data.RestoreOperationState
import sstu.grivvus.ym.data.RestoreOperationStatus
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.logHandledException
import java.io.IOException

data class RestoreStatusUi(
    val restoreId: String,
    val title: String,
    val isFinished: Boolean,
    val isFailed: Boolean,
    val errorMessage: String? = null,
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isSuperuser: Boolean = false,
    val includeImages: Boolean = true,
    val includeTranscodedTracks: Boolean = true,
    val isCreatingBackup: Boolean = false,
    val isSavingBackup: Boolean = false,
    val isStartingRestore: Boolean = false,
    val restoreStatus: RestoreStatusUi? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

sealed interface LibraryScreenEvent {
    data class RequestBackupSaveLocation(val suggestedFileName: String) : LibraryScreenEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val backupRestoreRepository: BackupRestoreRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    private val _events = MutableSharedFlow<LibraryScreenEvent>()
    private var pendingBackupArchive: DownloadedBackupArchive? = null
    private var restorePollingJob: Job? = null

    val uiState = _uiState.asStateFlow()
    val events: SharedFlow<LibraryScreenEvent> = _events.asSharedFlow()

    init {
        observeCurrentUser()
        refreshCurrentUser()
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

    fun createBackup() {
        if (!_uiState.value.isSuperuser ||
            _uiState.value.isCreatingBackup ||
            _uiState.value.isSavingBackup ||
            _uiState.value.isStartingRestore ||
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
                val archive = backupRestoreRepository.createBackupArchive(
                    options = BackupCreationOptions(
                        includeImages = _uiState.value.includeImages,
                        includeTranscodedTracks = _uiState.value.includeTranscodedTracks,
                    ),
                )
                pendingBackupArchive = archive
                _events.emit(
                    LibraryScreenEvent.RequestBackupSaveLocation(
                        suggestedFileName = archive.suggestedFileName,
                    ),
                )
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
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingBackup = true, errorMessage = null, infoMessage = null) }
            try {
                backupRestoreRepository.saveBackupArchive(archive, destinationUri)
                _uiState.update { it.copy(infoMessage = "Backup saved successfully") }
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
            _uiState.value.isSavingBackup ||
            _uiState.value.isStartingRestore ||
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
                            state = RestoreOperationState.PENDING,
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
                        isLoading = false,
                        isSuperuser = currentUser?.isSuperuser == true,
                    )
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

    private fun startRestorePolling(restoreId: String) {
        restorePollingJob?.cancel()
        restorePollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val status = backupRestoreRepository.getRestoreStatus(restoreId)
                    _uiState.update { state ->
                        state.copy(
                            restoreStatus = status.toUi(),
                            errorMessage = if (status.state == RestoreOperationState.ERROR) {
                                status.errorMessage ?: "Restore failed"
                            } else {
                                state.errorMessage
                            },
                        )
                    }
                    if (status.isTerminal) {
                        if (status.state == RestoreOperationState.FINISHED) {
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
                delay(RESTORE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshLocalStateAfterRestore() {
        try {
            backupRestoreRepository.refreshLocalStateAfterRestore()
            _uiState.update { state ->
                state.copy(infoMessage = "Restore finished. Local data refreshed.")
            }
        } catch (_: SessionExpiredException) {
            throw SessionExpiredException()
        } catch (error: Exception) {
            error.logHandledException("LibraryViewModel.refreshLocalStateAfterRestore")
            _uiState.update { state ->
                state.copy(errorMessage = "Restore finished, but local sync failed: ${error.toReadableMessage()}")
            }
        }
    }

    private fun hasRunningRestore(): Boolean {
        val restoreStatus = _uiState.value.restoreStatus ?: return false
        return !restoreStatus.isFinished && !restoreStatus.isFailed
    }

    private fun RestoreOperationStatus.toUi(): RestoreStatusUi {
        return when (state) {
            RestoreOperationState.PENDING -> RestoreStatusUi(
                restoreId = restoreId,
                title = "Restore queued",
                isFinished = false,
                isFailed = false,
            )

            RestoreOperationState.STARTED -> RestoreStatusUi(
                restoreId = restoreId,
                title = "Restore in progress",
                isFinished = false,
                isFailed = false,
            )

            RestoreOperationState.FINISHED -> RestoreStatusUi(
                restoreId = restoreId,
                title = "Restore finished",
                isFinished = true,
                isFailed = false,
            )

            RestoreOperationState.ERROR -> RestoreStatusUi(
                restoreId = restoreId,
                title = "Restore failed",
                isFinished = false,
                isFailed = true,
                errorMessage = errorMessage,
            )
        }
    }

    private fun Throwable.toReadableMessage(): String {
        return when (this) {
            is UnauthorizedApiException -> "Authentication required"
            is ClientApiException -> when (statusCode) {
                403 -> "Superuser access required"
                409 -> "Restore operation is already running"
                else -> message.takeIf { it.isNotBlank() } ?: "Request failed"
            }

            is ApiException -> message.takeIf { it.isNotBlank() } ?: "Server request failed"
            is IOException -> message?.takeIf { it.isNotBlank() } ?: "Network request failed"
            else -> message?.takeIf { it.isNotBlank() } ?: "Unexpected error"
        }
    }

    override fun onCleared() {
        restorePollingJob?.cancel()
        pendingBackupArchive?.file?.delete()
        pendingBackupArchive = null
        super.onCleared()
    }

    private companion object {
        private const val RESTORE_POLL_INTERVAL_MS = 2_000L
    }
}

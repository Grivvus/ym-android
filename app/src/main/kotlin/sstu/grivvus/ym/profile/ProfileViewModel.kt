package sstu.grivvus.ym.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.AppLanguage
import sstu.grivvus.ym.data.AppLanguageRepository
import sstu.grivvus.ym.data.PlaybackPreferencesRepository
import sstu.grivvus.ym.WhileUiSubscribed
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val email: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val serverHost: String = "",
    val serverPort: String = "8000",
    val errorMsg: UiText? = null,
    val infoMsg: UiText? = null,
    val avatarUri: Uri? = null,
    val preferredTrackQuality: TrackQuality = TrackQuality.STANDARD,
    val selectedAppLanguage: AppLanguage = AppLanguage.SYSTEM_DEFAULT,
)

sealed interface ProfileEvent {
    data object LoggedOut : ProfileEvent
    data object ServerSetupRequired : ProfileEvent
}

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository,
    private val serverInfoRepository: ServerInfoRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val appLanguageRepository: AppLanguageRepository,
) : ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _serverHost: MutableStateFlow<String> = MutableStateFlow("")
    private val _serverPort: MutableStateFlow<String> = MutableStateFlow("8000")
    private val _errorMsg: MutableStateFlow<UiText?> = MutableStateFlow(null)
    private val _infoMsg: MutableStateFlow<UiText?> = MutableStateFlow(null)
    private val _avatarUri: MutableStateFlow<Uri?> = MutableStateFlow(null)
    private val _preferredTrackQuality: MutableStateFlow<TrackQuality> = MutableStateFlow(
        playbackPreferencesRepository.currentPreferredTrackQuality(),
    )
    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ProfileUiState> =
        combine(
            combine(
                _username, _email, _isLoading, _errorMsg, _avatarUri
            ) { username, email, isLoading, errorMsg, avatarUri ->
                ProfileUiState(
                    username = username,
                    email = email,
                    isLoading = isLoading,
                    errorMsg = errorMsg,
                    avatarUri = avatarUri,
                )
            },
            combine(
                _isRefreshing,
                _serverHost,
                _serverPort,
                _preferredTrackQuality,
                appLanguageRepository.selectedLanguage,
            ) { isRefreshing, serverHost, serverPort, preferredTrackQuality, selectedAppLanguage ->
                ProfileUiState(
                    isRefreshing = isRefreshing,
                    serverHost = serverHost,
                    serverPort = serverPort,
                    preferredTrackQuality = preferredTrackQuality,
                    selectedAppLanguage = selectedAppLanguage,
                )
            },
            _infoMsg,
        ) { baseState, otherState, infoMsg ->
            ProfileUiState(
                username = baseState.username,
                email = baseState.email,
                isLoading = baseState.isLoading,
                isRefreshing = otherState.isRefreshing,
                serverHost = otherState.serverHost,
                serverPort = otherState.serverPort,
                errorMsg = baseState.errorMsg,
                infoMsg = infoMsg,
                avatarUri = baseState.avatarUri,
                preferredTrackQuality = otherState.preferredTrackQuality,
                selectedAppLanguage = otherState.selectedAppLanguage,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        _isLoading.value = true
        appLanguageRepository.refreshSelectedLanguage()
        loadUserFromLocal()
        loadServerSettings()
        _isLoading.value = false
    }

    private fun loadUserFromLocal() {
        viewModelScope.launch {
            applyCurrentUser()
        }
    }

    private fun loadServerSettings() {
        viewModelScope.launch {
            applyCurrentServerSettings()
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                userRepository.updateLocalUserFromNetwork()
                applyCurrentUser()
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (e: Exception) {
                e.logHandledException("ProfileViewModel.refreshUser")
                _infoMsg.value = null
                _errorMsg.value =
                    UiText.StringResource(R.string.profile_error_server_unreachable_local_data)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissErrorMessage() {
        _errorMsg.value = null
        _infoMsg.value = null
    }

    fun changeEmail(value: String) {
        _email.value = value
    }

    fun changeUsername(value: String) {
        _username.value = value
    }

    fun changePreferredTrackQuality(value: TrackQuality) {
        _preferredTrackQuality.value = value
    }

    fun changeAppLanguage(value: AppLanguage) {
        appLanguageRepository.applyLanguage(value)
    }

    fun logOut(clearServerInfo: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                userRepository.logout(clearServerInfo = clearServerInfo)
                _events.emit(
                    if (clearServerInfo) {
                        ProfileEvent.ServerSetupRequired
                    } else {
                        ProfileEvent.LoggedOut
                    },
                )
            } catch (e: Exception) {
                e.logHandledException("ProfileViewModel.logOut")
                _infoMsg.value = null
                _errorMsg.value = UiText.StringResource(R.string.common_error_unexpected)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) = viewModelScope.launch {
        _isLoading.value = true
        try {
            context.contentResolver.openInputStream(uri)?.close()
            userRepository.updateCurrentUserAvatar(uri.toString())
            applyCurrentUser()
        } catch (_: SessionExpiredException) {
            return@launch
        } catch (e: Exception) {
            e.logHandledException("ProfileViewModel.uploadAvatar")
            _infoMsg.value = null
            _errorMsg.value = UiText.StringResource(R.string.profile_error_avatar_upload_failed)
        } finally {
            _isLoading.value = false
        }
    }

    fun tryToApplyChanges() = viewModelScope.launch {
        try {
            val currentUserData = userRepository.requireCurrentUser()
            val newUsername =
                if (_username.value != currentUserData.username) _username.value else null
            val newEmail = if (_email.value != currentUserData.email) _email.value else null
            val newTrackQuality = _preferredTrackQuality.value.takeIf {
                it != playbackPreferencesRepository.currentPreferredTrackQuality()
            }
            if (newEmail == null &&
                newUsername == null &&
                newTrackQuality == null
            ) {
                _infoMsg.value = null
                _errorMsg.value = UiText.StringResource(R.string.profile_error_nothing_to_save)
                return@launch
            }
            _isLoading.value = true

            if (newTrackQuality != null) {
                playbackPreferencesRepository.savePreferredTrackQuality(newTrackQuality)
            }

            if (newEmail != null || newUsername != null) {
                userRepository.applyChanges(newUsername, newEmail)
                applyCurrentUser()
            }
            _errorMsg.value = null
            _infoMsg.value = UiText.StringResource(R.string.profile_info_updated_successfully)
        } catch (_: SessionExpiredException) {
            return@launch
        } catch (e: ApiException) {
            e.logHandledException("ProfileViewModel.tryToApplyChanges")
            _infoMsg.value = null
            _errorMsg.value =
                if (e.statusCode in 400..499) {
                    UiText.StringResource(R.string.profile_error_username_or_email_not_unique)
                } else {
                    UiText.StringResource(R.string.common_error_server_try_again_later)
                }
        } catch (e: Exception) {
            e.logHandledException("ProfileViewModel.tryToApplyChanges")
            _infoMsg.value = null
            _errorMsg.value = UiText.StringResource(R.string.profile_error_update_unexpected)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun applyCurrentUser() {
        val currentUser = userRepository.getCurrentUser() ?: return
        changeUsername(currentUser.username)
        changeEmail(currentUser.email ?: "")
        _avatarUri.value = currentUser.avatarUri
    }

    private suspend fun applyCurrentServerSettings() {
        val currentServerSettings = serverInfoRepository.getServerInfo() ?: return
        _serverHost.value = currentServerSettings.host
        _serverPort.value = currentServerSettings.port
    }
}

package sstu.grivvus.ym.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.ym.WhileUiSubscribed
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.ChangeServerDto
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.data.network.core.ApiException
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val email: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val serverHost: String = "",
    val serverPort: String = "8000",
    val errorMsg: String? = null,
    val avatarUri: Uri? = null,
)

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository,
    private val serverInfoRepository: ServerInfoRepository,
) : ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _serverHost: MutableStateFlow<String> = MutableStateFlow("")
    private val _serverPort: MutableStateFlow<String> = MutableStateFlow("8000")
    private val _errorMsg: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _avatarUri: MutableStateFlow<Uri?> = MutableStateFlow(null)

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
                _serverPort
            ) { isRefreshing, serverHost, serverPort ->
                ProfileUiState(
                    isRefreshing = isRefreshing,
                    serverHost = serverHost,
                    serverPort = serverPort,
                )
            }
        ) { baseState, otherState ->
            ProfileUiState(
                username = baseState.username,
                email = baseState.email,
                isLoading = baseState.isLoading,
                isRefreshing = otherState.isRefreshing,
                serverHost = otherState.serverHost,
                serverPort = otherState.serverPort,
                errorMsg = baseState.errorMsg,
                avatarUri = baseState.avatarUri,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        _isLoading.value = true
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
                _errorMsg.value = "can't connect to the server, local data will be used"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissErrorMessage() {
        _errorMsg.value = null
    }

    fun changeEmail(value: String) {
        _email.value = value
    }

    fun changeUsername(value: String) {
        _username.value = value
    }

    fun changeServerHost(value: String) {
        _serverHost.value = value
    }

    fun changeServerPort(value: String) {
        _serverPort.value = value
    }

    fun logOut() {
        viewModelScope.launch {
            userRepository.logout()
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
            _errorMsg.value = "Failed to set avatar image"
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
            val changeServer = ChangeServerDto(
                if (_serverHost.value != serverInfoRepository.getServerInfo()?.host) _serverHost.value else null,
                if (_serverPort.value != serverInfoRepository.getServerInfo()?.port) _serverPort.value else null
            )
            if (newEmail == null && newUsername == null && changeServer.host == null && changeServer.port == null) {
                _errorMsg.value = "Nothing that can be saved"
                return@launch
            }
            _isLoading.value = true
            // я не проверяю правильность хоста/порта
            // а так же не проверяю, что сервер по этим данным доступен
            serverInfoRepository.saveServerInfo(_serverHost.value, _serverPort.value)
            applyCurrentServerSettings()

            if (newEmail == null && newUsername == null) {
                return@launch
            }
            userRepository.applyChanges(newUsername, newEmail)
            applyCurrentUser()
            _errorMsg.value = "Profile updated successfully"
        } catch (_: SessionExpiredException) {
            return@launch
        } catch (e: ApiException) {
            _errorMsg.value =
                if (e.statusCode != null && e.statusCode!! >= 400 && e.statusCode!! < 500) {
                    "New username or email are not unique"
                } else {
                    "Server error. Please try again later"
                }
        } catch (e: Exception) {
            _errorMsg.value = "Can't update profile due to unexpected error"
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

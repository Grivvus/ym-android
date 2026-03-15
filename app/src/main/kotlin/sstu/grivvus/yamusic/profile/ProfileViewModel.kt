package sstu.grivvus.yamusic.profile

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
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.data.network.ChangeUserDto
import sstu.grivvus.yamusic.data.network.isServerSideError
import java.io.IOException
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val email: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMsg: String? = null,
    val avatarUri: Uri? = null,
)

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
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
            _isRefreshing
        ) { baseState, isRefreshing ->
            ProfileUiState(
                username = baseState.username,
                email = baseState.email,
                isLoading = baseState.isLoading,
                isRefreshing = isRefreshing,
                errorMsg = baseState.errorMsg,
                avatarUri = baseState.avatarUri,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        loadUserFromLocal()
    }

    private fun loadUserFromLocal() {
        viewModelScope.launch {
            _isLoading.value = true
            applyCurrentUser()
            _isLoading.value = false
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                userRepository.updateLocalUserFromNetwork()
                applyCurrentUser()
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

    fun logOut() = viewModelScope.launch {
        userRepository.localDataSource.clearTable()
    }

    fun uploadAvatar(context: Context, uri: Uri) = viewModelScope.launch {
        _isLoading.value = true
        try {
            context.contentResolver.openInputStream(uri)?.close()
            userRepository.updateCurrentUserAvatar(uri.toString())
            applyCurrentUser()
        } catch (e: Exception) {
            _errorMsg.value = "Failed to set avatar image"
        } finally {
            _isLoading.value = false
        }
    }

    fun tryToApplyChanges() = viewModelScope.launch {
        val currentUserData = userRepository.getCurrentUser()
        assert(currentUserData != null)
        try {
            val changeUser = ChangeUserDto(
                currentUserData!!.username,
                if (_username.value != currentUserData.username) _username.value else null,
                if (_email.value != currentUserData.email) _email.value else null
            )
            if (changeUser.newEmail == null && changeUser.newUsername == null) {
                _errorMsg.value = "Nothing that can be saved"
                return@launch
            }
            _isLoading.value = true
            userRepository.applyChanges(changeUser)
            applyCurrentUser()
            _errorMsg.value = "Profile updated successfully"
        } catch (e: IOException) {
            _errorMsg.value = if (e.isServerSideError()) {
                "Server error. Please try again later"
            } else {
                "New username or email are not unique"
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
}

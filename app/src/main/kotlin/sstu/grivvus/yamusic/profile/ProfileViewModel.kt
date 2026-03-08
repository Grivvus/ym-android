package sstu.grivvus.yamusic.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
    private val _errorMsg: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _avatarUri: MutableStateFlow<Uri?> = MutableStateFlow(null)

    val uiState: StateFlow<ProfileUiState> =
        combine(
            _username, _email, _isLoading, _errorMsg, _avatarUri
        ) { username, email, isLoading, errorMsg, avatarUri ->
            ProfileUiState(
                username, email, isLoading, errorMsg, avatarUri
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        viewModelScope.launch {
            val job = async { userRepository.updateLocalUserFromNetwork() }
            job.await()
            val currentUser = userRepository.getCurrentUser()
            changeUsername(currentUser.username)
            changeEmail(currentUser.email ?: "")
            _avatarUri.value = currentUser.avatarUri
            _isLoading.value = false
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

    private fun updateUri(uri: Uri) {
        _avatarUri.value = uri
    }

    fun uploadAvatar(context: Context, uri: Uri) = viewModelScope.launch {
        try {
            context.contentResolver.openInputStream(uri)?.close()
            userRepository.updateCurrentUserAvatar(uri.toString())
            val updatedUser = userRepository.getCurrentUser()
            updatedUser.avatarUri?.let { updateUri(it) }
        } catch (e: Exception) {
            _errorMsg.value = "Failed to set avatar image"
        }
    }

    fun tryToApplyChanges() = viewModelScope.launch {
        val currentUserData = userRepository.getCurrentUser()
        try {
            val changeUser = ChangeUserDto(
                currentUserData.username,
                if (_username.value != currentUserData.username) _username.value else null,
                if (_email.value != currentUserData.email) _email.value else null
            )
            if (changeUser.newEmail == null && changeUser.newUsername == null) {
                _errorMsg.value = "Nothing that can be saved"
                return@launch
            }
            userRepository.applyChanges(changeUser)
            val updatedUser = userRepository.getCurrentUser()
            changeUsername(updatedUser.username)
            changeEmail(updatedUser.email ?: "")
            _errorMsg.value = "Profile updated successfully"
        } catch (e: IOException) {
            _errorMsg.value = if (e.isServerSideError()) {
                "Server error. Please try again later"
            } else {
                "New username or email are not unique"
            }
        } catch (e: Exception) {
            _errorMsg.value = "Can't update profile due to unexpected error"
        }
    }
}

package sstu.grivvus.yamusic.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.network.ChangeUserDto
import sstu.grivvus.yamusic.data.network.downloadImage
import sstu.grivvus.yamusic.data.network.uploadImage
import java.io.File
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val email: String? = null,
    val isLoading: Boolean = false,
    val errorMsg: String? = null
)

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val _errorMsg: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<ProfileUiState> =
        combine(
            _username, _email, _isLoading, _errorMsg,
        ) {
            username, email, isLoading, errorMsg ->
            ProfileUiState(
                username, email, isLoading, errorMsg
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            changeUsername(currentUser.username)
            changeEmail(currentUser.email ?: "")
            downloadAvatar()
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

    fun logOut() = viewModelScope.launch{
        userRepository.localDataSource.clearTable()
    }

    fun uploadAvatar(context: Context, uri: Uri) = viewModelScope.launch{
        val inputStream = context.contentResolver.openInputStream(uri)
        val username = userRepository.getCurrentUser().username
        if (inputStream == null) {
            _errorMsg.value = "Failing to upload image"
            return@launch
        }
        val file = File(context.cacheDir, "temp_image.jpg")
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        uploadImage(file, username)
    }

    fun downloadAvatar() = viewModelScope.launch {
        try {
            val username = userRepository.getCurrentUser().username
            val data = downloadImage(username)
            if (data == null) {
                _errorMsg.value = "Can't download avatar"
                return@launch
            }
        } catch (e: Exception) {
            _errorMsg.value = "Unknown network error"
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
        } catch(e: Exception) {
            _errorMsg.value = "New username or email are not unique"
        }
    }
}
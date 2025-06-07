package sstu.grivvus.yamusic.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val errorMsg: String? = null,
    val avatarUri: Uri? = null,
)

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context,
): ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val _errorMsg: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _avatarUri: MutableStateFlow<Uri?> = MutableStateFlow(null)

    val uiState: StateFlow<ProfileUiState> =
        combine(
            _username, _email, _isLoading, _errorMsg, _avatarUri
        ) {
            username, email, isLoading, errorMsg, avatarUri ->
            ProfileUiState(
                username, email, isLoading, errorMsg, avatarUri
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    init {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            changeUsername(currentUser.username)
            changeEmail(currentUser.email ?: "")
            if (currentUser.avatarUri != null) {
                _avatarUri.value = currentUser.avatarUri.toUri()
            } else {
                downloadAvatar()
            }
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

    private fun updateUri(uri: Uri) {
        _avatarUri.value = uri
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
        downloadAvatar()
    }

    fun downloadAvatar() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val username = userRepository.getCurrentUser().username
            if (username == "") {
                throw IllegalArgumentException("empty username")
            }
            val data = downloadImage(username)
            if (data == null) {
                _errorMsg.value = "Can't download avatar"
                return@launch
            }
            val fileName = "avatar"
            val dir = File(context.filesDir, "user")
            if (!dir.exists()) dir.mkdirs()
            val outputFile = File(dir, fileName)
            data.use {input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val newUri = "${outputFile.toUri()}?t=${System.currentTimeMillis()}".toUri()
            updateUri(newUri)
            userRepository.updateCurrentUserAvatar(newUri.toString())
        } catch (e: Exception) {
            _errorMsg.value = "Unknown network error"
            Log.e("ProfileViewModel", e.toString())
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
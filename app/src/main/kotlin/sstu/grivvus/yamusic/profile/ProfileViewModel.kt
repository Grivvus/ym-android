package sstu.grivvus.yamusic.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.UserRepository
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val email: String? = null,
    val isAvatarExistLocally: Boolean = false,
    val isLoading: Boolean = false,
    val avatarFileName: String? = null,
)

@HiltViewModel
class ProfileViewModel
@Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _email: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _isAvatarExistLocally: MutableStateFlow<Boolean>
        = MutableStateFlow(false)
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _avatarFileName: MutableStateFlow<String?>
        = MutableStateFlow(null)

    val uiState: StateFlow<ProfileUiState> =
        combine(
            _username, _email,
            _isAvatarExistLocally, _isLoading, _avatarFileName,
        ) {
            username, email, isAvatarExistLocally, isLoading, avatarFileName ->
            ProfileUiState(
                username, email,
                isAvatarExistLocally, isLoading, avatarFileName,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, ProfileUiState())

    fun changePassword() {}

    fun uploadAvatar(uri: Uri) {}
}
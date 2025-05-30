package sstu.grivvus.yamusic.passwordChange

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
import sstu.grivvus.yamusic.data.network.NetworkUserLogin
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject


data class PasswordChangeUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class PasswordChangeViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _currentPassword: MutableStateFlow<String> = MutableStateFlow("")
    private val _newPassword: MutableStateFlow<String> = MutableStateFlow("")
    private val _newPasswordConfirm: MutableStateFlow<String> = MutableStateFlow("")
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _success: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val uiState: StateFlow<PasswordChangeUiState> = combine(
        combine(_currentPassword, _newPassword, _newPasswordConfirm) {
            current, new, confirm ->
            Triple(current, new, confirm)
        },
        combine(_isLoading, _errorMessage, _success) { loading, error, success ->
            Triple(loading, error, success)
        }
    ) { (currentPassword, newPassword, newPasswordConfirm),
        (isLoading, errorMessage, success) ->
        PasswordChangeUiState(
            currentPassword = currentPassword,
            newPassword = newPassword,
            newPasswordConfirm = newPasswordConfirm,
            isLoading = isLoading,
            errorMessage = errorMessage,
            success = success
        )
    }.stateIn(
        viewModelScope,
        WhileUiSubscribed,
        PasswordChangeUiState()
    )


    fun changePassword() = viewModelScope.launch{
        if (_newPassword.value.length < 6) {
            _errorMessage.value = "password's length should be 6 symbols or more"
        } else if (_newPassword.value != _newPasswordConfirm.value) {
            _errorMessage.value = "new password and confirm didn't match"
        } else {
            try {
                userRepository.changePassword(
                    _currentPassword.value,
                    _newPassword.value,
                )
            } catch (e: IOException) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateCurrentPassword(value: String) {
        _currentPassword.value = value
    }

    fun updateNewPassword(value: String) {
        _newPassword.value = value
    }

    fun updateConfirmPassword(value: String) {
        _newPasswordConfirm.value = value
    }

    fun updateSuccessFlag(value: Boolean) {
        _success.value = value
    }

    fun clear() {
        _newPassword.value = ""
        _newPasswordConfirm. value = ""
        _currentPassword.value = ""
        _errorMessage.value = null
        _isLoading.value = false
        _success.value = false
    }
}
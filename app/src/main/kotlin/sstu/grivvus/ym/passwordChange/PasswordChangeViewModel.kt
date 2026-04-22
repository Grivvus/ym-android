package sstu.grivvus.ym.passwordChange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.WhileUiSubscribed
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject


data class PasswordChangeUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
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
    private val _errorMessage: MutableStateFlow<UiText?> = MutableStateFlow(null)
    private val _success: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val uiState: StateFlow<PasswordChangeUiState> = combine(
        combine(_currentPassword, _newPassword, _newPasswordConfirm) { current, new, confirm ->
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


    fun changePassword() = viewModelScope.launch {
        if (_isLoading.value) return@launch

        _errorMessage.value = null
        _success.value = false

        if (_currentPassword.value.isBlank()) {
            _errorMessage.value =
                UiText.StringResource(R.string.password_change_error_current_password_required)
            return@launch
        }

        if (_newPassword.value.length < 6) {
            _errorMessage.value =
                UiText.StringResource(R.string.common_validation_password_min_length)
            return@launch
        }

        if (_newPassword.value != _newPasswordConfirm.value) {
            _errorMessage.value =
                UiText.StringResource(R.string.password_change_error_new_password_confirm_mismatch)
            return@launch
        }

        _isLoading.value = true
        try {
            userRepository.changePassword(
                _currentPassword.value,
                _newPassword.value,
            )
            _success.value = true
        } catch (e: IOException) {
            _errorMessage.value = e.message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.password_change_error_failed)
        } catch (_: Exception) {
            _errorMessage.value =
                UiText.StringResource(R.string.password_change_error_unexpected)
        } finally {
            _isLoading.value = false
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

    fun clear() {
        _newPassword.value = ""
        _newPasswordConfirm.value = ""
        _currentPassword.value = ""
        _errorMessage.value = null
        _isLoading.value = false
        _success.value = false
    }
}

package sstu.grivvus.ym.register

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
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.ui.UiText
import timber.log.Timber
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val passwordCheck: String = "",
    val showError: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class RegisterViewModel
@Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _password: MutableStateFlow<String> = MutableStateFlow("")
    private val _passwordCheck: MutableStateFlow<String> = MutableStateFlow("")
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<UiText?> = MutableStateFlow(null)

    val uiState: StateFlow<RegisterUiState> =
        combine(
            _username,
            _password,
            _passwordCheck,
            _showError,
            _errorMessage,
        ) {
            // not quite understand what happens here
                username, password, passwordCheck, showError, errorMessage ->
            RegisterUiState(username, password, passwordCheck, showError, errorMessage)
        }.stateIn(viewModelScope, WhileUiSubscribed, RegisterUiState())

    fun proceedRegistration(onSuccess: () -> Unit) =
        viewModelScope.launch {
            if (
                _username.value == "" ||
                _password.value.length < 6 ||
                _password.value != _passwordCheck.value
            ) {
                _showError.value = true
                if (_username.value.isBlank()) {
                    _errorMessage.value =
                        UiText.StringResource(R.string.common_validation_username_required)
                } else if (_password.value.length < 6) {
                    _errorMessage.value =
                        UiText.StringResource(R.string.common_validation_password_min_length)
                } else if (_password.value != _passwordCheck.value) {
                    _errorMessage.value =
                        UiText.StringResource(R.string.common_validation_passwords_must_match)
                }
            } else {
                try {
                    userRepository.register(
                        _username.value,
                        _password.value
                    )
                    onSuccess()
                } catch (e: ApiException) {
                    _showError.value = true
                    _errorMessage.value =
                        if (e.statusCode in 400..499) {
                            UiText.StringResource(R.string.register_error_username_taken)
                        } else {
                            UiText.StringResource(R.string.common_error_server_try_again_later)
                        }
                } catch (e: Exception) {
                    Timber.tag("NetworkError").e(e)
                    _showError.value = true
                    _errorMessage.value = UiText.StringResource(R.string.register_error_server)
                }
            }
        }

    fun dismissErrorMessage() {
        _showError.value = false
        _errorMessage.value = null
    }

    fun updateUsername(value: String) {
        _username.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun updatePasswordCheck(value: String) {
        _passwordCheck.value = value
    }

    fun clearForm() {
        _username.value = ""
        _password.value = ""
        _passwordCheck.value = ""
        _showError.value = false
        _errorMessage.value = null
    }
}

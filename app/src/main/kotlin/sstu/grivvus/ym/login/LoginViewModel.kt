package sstu.grivvus.ym.login

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
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val showError: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class LoginViewModel
@Inject
constructor(
    private val userRepository: UserRepository,
    private val serverInfoRepository: ServerInfoRepository,
) : ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _password: MutableStateFlow<String> = MutableStateFlow("")
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<UiText?> = MutableStateFlow(null)

    val uiState: StateFlow<LoginUiState> =
        combine(
            _username,
            _password,
            _showError,
            _errorMessage,
        ) {
            // not quite understand what happens here
                username, password, showError, errorMessage ->
            LoginUiState(username, password, showError, errorMessage)
        }.stateIn(viewModelScope, WhileUiSubscribed, LoginUiState())

    fun proceedLogin(onSuccess: () -> Unit) =
        viewModelScope.launch {
            if (
                _username.value == "" ||
                _password.value.length < 6
            ) {
                _showError.value = true
                if (_username.value.isBlank()) {
                    _errorMessage.value =
                        UiText.StringResource(R.string.common_validation_username_required)
                } else if (_password.value.length < 6) {
                    _errorMessage.value =
                        UiText.StringResource(R.string.common_validation_password_min_length)
                }
            } else {
                try {
                    userRepository.login(_username.value, _password.value)
                    onSuccess()
                } catch (e: ApiException) {
                    _showError.value = true
                    if (e.statusCode in 400..499) {
                        _errorMessage.value =
                            UiText.StringResource(R.string.login_error_wrong_credentials)
                    } else {
                        _errorMessage.value = UiText.StringResource(R.string.login_error_server)
                    }
                } catch (e: Exception) {
                    Timber.tag("NetworkError").e(e)
                    _showError.value = true
                    _errorMessage.value =
                        UiText.StringResource(R.string.login_error_unknown_server)
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

    fun clearForm() {
        _username.value = ""
        _password.value = ""
        _showError.value = false
        _errorMessage.value = null
    }

    fun resetServerInfo(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                serverInfoRepository.clearServerInfo()
                onSuccess()
            } catch (e: Exception) {
                e.logHandledException("LoginViewModel.resetServerInfo")
                _showError.value = true
                _errorMessage.value = UiText.StringResource(R.string.common_error_unexpected)
            }
        }
    }
}

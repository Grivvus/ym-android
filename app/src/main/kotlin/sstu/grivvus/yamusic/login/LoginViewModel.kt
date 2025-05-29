package sstu.grivvus.yamusic.login

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
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val showError: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val _username: MutableStateFlow<String> = MutableStateFlow("")
        private val _password: MutableStateFlow<String> = MutableStateFlow("")
        private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
        private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

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
                    if (_username.value == "") {
                        _errorMessage.value = "username shouldn't be empty"
                    } else if (_password.value.length < 6) {
                        _errorMessage.value = "password's length couldn't be less than 6 symbols"
                    }
                } else {
                    try {
                        userRepository.login(NetworkUserLogin(_username.value, _password.value))
                        onSuccess()
                    } catch(e: Exception) {
                        Timber.tag("NetworkError").e(e)
                        _showError.value = true
                        _errorMessage.value = "Can't proceed login due to server error"
                        throw e
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
        }
    }
package sstu.grivvus.yamusic.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.NavigationActions
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val passwordCheck: String = "",
    val showError: Boolean = false,
    val errorMessage: String? = null,
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
    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

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
                if (_username.value == "") {
                    _errorMessage.value = "username shouldn't be empty"
                } else if (_password.value.length < 6) {
                    _errorMessage.value = "password's length should be 6 symbols or more"
                } else if (_password.value != _passwordCheck.value) {
                    _errorMessage.value = "passwords should be equal"
                }
            } else {
                try {
                    userRepository.register(NetworkUserCreate(_username.value, null, _password.value))
                    onSuccess()
                } catch (e: IOException) {
                    _showError.value = true
                    _errorMessage.value = "This username is already used"
                } catch (e: Exception) {
                    Timber.tag("NetworkError").e(e)
                    _showError.value = true
                    _errorMessage.value = "Can't proceed registration due to server error"
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

    fun updatePasswordCheck(value: String) {
        _passwordCheck.value = value
    }

    fun clearForm() {
        _username.value = ""
        _password.value = ""
        _passwordCheck.value = ""
        _showError.value = false
    }
}
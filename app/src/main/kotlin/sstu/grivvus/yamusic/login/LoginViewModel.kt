package sstu.grivvus.yamusic.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.network.NetworkUser
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import javax.inject.Inject

data class LoginUiState (
    val username: String = "",
    val password: String = "",
    val showError: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
):  ViewModel() {
    private val _username: MutableStateFlow<String> = MutableStateFlow("")
    private val _password: MutableStateFlow<String> = MutableStateFlow("")
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val uiState: StateFlow<LoginUiState> = combine(_username, _password, _showError) {
        // not quite understand what happens here
            username, password, showError ->
        LoginUiState(username, password, showError)
    }
        .stateIn(viewModelScope, WhileUiSubscribed, LoginUiState())

    fun proceedLogin() = viewModelScope.launch {

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
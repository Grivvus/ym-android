package sstu.grivvus.yamusic.serverSetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import sstu.grivvus.yamusic.WhileUiSubscribed
import javax.inject.Inject

data class ServerSetupUiState(
    val host: String = "10.0.2.2",
    val port: Int = 8000,
    val showError: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ServerSetupViewModel
@Inject constructor() : ViewModel() {
    private val _host: MutableStateFlow<String> = MutableStateFlow("10.0.2.2")
    private val _port: MutableStateFlow<Int> = MutableStateFlow(8000)
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<ServerSetupUiState> = combine(
        _host, _port, _showError, _errorMessage
    ) { host, port, showError, errorMessage ->
        ServerSetupUiState(host, port, showError, errorMessage)
    }.stateIn(viewModelScope, WhileUiSubscribed, ServerSetupUiState())

    fun proceed(onSuccess: () -> Unit) {}

    fun dismissErrorMessage() {
        _showError.value = false
        _errorMessage.value = null
    }

    fun clearForm() {
        _host.value = ""
        _port.value = 8000
        _showError.value = false
        _errorMessage.value = null
    }

    fun updateHost(value: String) {
        _host.value = value
    }

    fun updatePort(value: String) {
        val portInt = value.toIntOrNull()
        if (portInt == null) {
            _errorMessage.value = "Port value must be a valid int"
            _showError.value = true
            return
        }
        _port.value = portInt
    }
}
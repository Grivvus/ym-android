package sstu.grivvus.yamusic.serverSetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.Settings
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.network.pingServer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject

data class ServerSetupUiState(
    val host: String = "10.0.2.2",
    val port: String = "8000",
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ServerSetupViewModel
@Inject
constructor(
    private val serverInfoRepository: ServerInfoRepository,
) : ViewModel() {
    private val _host: MutableStateFlow<String> = MutableStateFlow("10.0.2.2")
    private val _port: MutableStateFlow<String> = MutableStateFlow("8000")
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<ServerSetupUiState> = combine(
        _host, _port, _isLoading, _showError, _errorMessage
    ) { host, port, isLoading, showError, errorMessage ->
        ServerSetupUiState(host, port, isLoading, showError, errorMessage)
    }.stateIn(viewModelScope, WhileUiSubscribed, ServerSetupUiState())

    fun proceed(onSuccess: () -> Unit) =
        viewModelScope.launch {
            val rawHost = _host.value.trim()
            val portValue = _port.value.trim()
            val portInt = portValue.toIntOrNull()

            if (rawHost.isBlank()) {
                showError("Host value must not be empty")
                return@launch
            }
            if (portInt == null || portInt !in 1..65535) {
                showError("Port value must be in range 1..65535")
                return@launch
            }

            val host = normalizeHost(rawHost) ?: return@launch
            if ("http://$host:$portInt/ping".toHttpUrlOrNull() == null) {
                showError("Host or port format is invalid")
                return@launch
            }

            _isLoading.value = true
            try {
                pingServer(host, portInt)
                serverInfoRepository.saveServerInfo(host, portValue)
                Settings.configureApi(host, portValue)
                onSuccess()
            } catch (e: IOException) {
                showError("Unable to connect to server. Check host, port and /ping endpoint")
            } catch (e: Exception) {
                showError("Can't proceed setup due to server error")
            } finally {
                _isLoading.value = false
            }
        }

    fun dismissErrorMessage() {
        _showError.value = false
        _errorMessage.value = null
    }

    fun clearForm() {
        _host.value = ""
        _port.value = ""
        _showError.value = false
        _errorMessage.value = null
    }

    fun updateHost(value: String) {
        _host.value = value
    }

    fun updatePort(value: String) {
        _port.value = value.filter { it.isDigit() }
    }

    private fun showError(message: String) {
        _showError.value = true
        _errorMessage.value = message
    }

    private fun normalizeHost(value: String): String? {
        if (value.contains(' ')) {
            showError("Host should not contain spaces")
            return null
        }

        if (!value.contains("://")) {
            return value
        }

        val parsed = value.toHttpUrlOrNull()
        if (parsed == null) {
            showError("Invalid URL format")
            return null
        }
        if (parsed.encodedPath != "/" || parsed.query != null || parsed.fragment != null) {
            showError("Use only host URL without path or query")
            return null
        }

        return parsed.host
    }
}

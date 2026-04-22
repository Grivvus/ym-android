package sstu.grivvus.ym.serverSetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sstu.grivvus.ym.R
import sstu.grivvus.ym.WhileUiSubscribed
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.network.remote.server.ServerProbeRemoteDataSource
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import java.io.IOException
import javax.inject.Inject

data class ServerSetupUiState(
    val host: String = "10.0.2.2",
    val port: String = "8000",
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class ServerSetupViewModel
@Inject
constructor(
    private val serverInfoRepository: ServerInfoRepository,
    private val serverProbeRemoteDataSource: ServerProbeRemoteDataSource,
) : ViewModel() {
    private val _host: MutableStateFlow<String> = MutableStateFlow("10.0.2.2")
    private val _port: MutableStateFlow<String> = MutableStateFlow("8000")
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _showError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _errorMessage: MutableStateFlow<UiText?> = MutableStateFlow(null)

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
                showError(UiText.StringResource(R.string.server_setup_error_host_required))
                return@launch
            }
            if (portInt == null || portInt !in 1..65535) {
                showError(UiText.StringResource(R.string.server_setup_error_port_out_of_range))
                return@launch
            }

            val host = normalizeHost(rawHost) ?: return@launch
            if ("http://$host:$portInt/ping".toHttpUrlOrNull() == null) {
                showError(
                    UiText.StringResource(R.string.server_setup_error_invalid_host_or_port_format),
                )
                return@launch
            }

            _isLoading.value = true
            try {
                serverProbeRemoteDataSource.ping(host, portInt)
                serverInfoRepository.saveServerInfo(host, portValue)
                onSuccess()
            } catch (e: IOException) {
                e.logHandledException("ServerSetupViewModel.proceed")
                showError(UiText.StringResource(R.string.server_setup_error_connection_failed))
            } catch (e: Exception) {
                e.logHandledException("ServerSetupViewModel.proceed")
                showError(UiText.StringResource(R.string.server_setup_error_server))
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

    private fun showError(message: UiText) {
        _showError.value = true
        _errorMessage.value = message
    }

    private fun normalizeHost(value: String): String? {
        if (value.contains(' ')) {
            showError(UiText.StringResource(R.string.server_setup_error_host_spaces))
            return null
        }

        if (!value.contains("://")) {
            return value
        }

        val parsed = value.toHttpUrlOrNull()
        if (parsed == null) {
            showError(UiText.StringResource(R.string.server_setup_error_invalid_url))
            return null
        }
        if (parsed.encodedPath != "/" || parsed.query != null || parsed.fragment != null) {
            showError(
                UiText.StringResource(R.string.server_setup_error_url_without_path_or_query),
            )
            return null
        }

        return parsed.host
    }
}

package sstu.grivvus.ym.passwordReset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.WhileUiSubscribed
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText

enum class PasswordResetStep {
    RequestCode,
    ConfirmReset,
}

data class PasswordResetUiState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val step: PasswordResetStep = PasswordResetStep.RequestCode,
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: UiText? = null,
    val infoMessage: UiText? = null,
    val resetCompleted: Boolean = false,
)

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _email = MutableStateFlow("")
    private val _code = MutableStateFlow("")
    private val _newPassword = MutableStateFlow("")
    private val _newPasswordConfirm = MutableStateFlow("")
    private val _step = MutableStateFlow(PasswordResetStep.RequestCode)
    private val _isLoading = MutableStateFlow(false)
    private val _showError = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    private val _infoMessage = MutableStateFlow<UiText?>(null)
    private val _resetCompleted = MutableStateFlow(false)

    val uiState: StateFlow<PasswordResetUiState> = combine(
        combine(_email, _code, _newPassword, _newPasswordConfirm) { email, code, password, confirm ->
            PasswordResetFormValues(email, code, password, confirm)
        },
        combine(
            combine(_step, _isLoading, _showError) { step, isLoading, showError ->
                PasswordResetProgressValues(step, isLoading, showError)
            },
            combine(_errorMessage, _infoMessage, _resetCompleted) { error, info, completed ->
                PasswordResetMessageValues(error, info, completed)
            },
        ) { progress, messages ->
            PasswordResetStatusValues(
                step = progress.step,
                isLoading = progress.isLoading,
                showError = progress.showError,
                errorMessage = messages.errorMessage,
                infoMessage = messages.infoMessage,
                resetCompleted = messages.resetCompleted,
            )
        },
    ) { form, status ->
        PasswordResetUiState(
            email = form.email,
            code = form.code,
            newPassword = form.newPassword,
            newPasswordConfirm = form.newPasswordConfirm,
            step = status.step,
            isLoading = status.isLoading,
            showError = status.showError,
            errorMessage = status.errorMessage,
            infoMessage = status.infoMessage,
            resetCompleted = status.resetCompleted,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, PasswordResetUiState())

    fun requestResetCode() = viewModelScope.launch {
        if (_isLoading.value) return@launch

        clearMessages()
        val email = _email.value.trim()
        if (!validateEmail(email)) return@launch

        _isLoading.value = true
        try {
            userRepository.requestPasswordReset(email)
            _email.value = email
            _step.value = PasswordResetStep.ConfirmReset
            _infoMessage.value = UiText.StringResource(R.string.password_reset_info_code_sent)
        } catch (e: ApiException) {
            e.logHandledException("PasswordResetViewModel.requestResetCode")
            showError(e.toRequestErrorMessage())
        } catch (e: Exception) {
            e.logHandledException("PasswordResetViewModel.requestResetCode")
            showError(UiText.StringResource(R.string.password_reset_error_request_failed))
        } finally {
            _isLoading.value = false
        }
    }

    fun confirmPasswordReset() = viewModelScope.launch {
        if (_isLoading.value) return@launch

        clearMessages()
        val email = _email.value.trim()
        val code = _code.value.trim()
        if (!validateEmail(email)) return@launch
        if (code.isBlank()) {
            showError(UiText.StringResource(R.string.password_reset_error_code_required))
            return@launch
        }
        if (_newPassword.value.length < MIN_PASSWORD_LENGTH) {
            showError(UiText.StringResource(R.string.common_validation_password_min_length))
            return@launch
        }
        if (_newPassword.value != _newPasswordConfirm.value) {
            showError(UiText.StringResource(R.string.common_validation_passwords_must_match))
            return@launch
        }

        _isLoading.value = true
        try {
            userRepository.confirmPasswordReset(
                email = email,
                code = code,
                newPassword = _newPassword.value,
            )
            _email.value = email
            _code.value = code
            _resetCompleted.value = true
            _infoMessage.value = UiText.StringResource(R.string.password_reset_success)
        } catch (e: IOException) {
            e.logHandledException("PasswordResetViewModel.confirmPasswordReset")
            showError(
                if (e is ApiException) {
                    e.toConfirmErrorMessage()
                } else {
                    UiText.StringResource(R.string.password_reset_error_request_failed)
                },
            )
        } catch (e: Exception) {
            e.logHandledException("PasswordResetViewModel.confirmPasswordReset")
            showError(UiText.StringResource(R.string.common_error_unexpected))
        } finally {
            _isLoading.value = false
        }
    }

    fun updateEmail(value: String) {
        _email.value = value
    }

    fun updateCode(value: String) {
        _code.value = value
    }

    fun updateNewPassword(value: String) {
        _newPassword.value = value
    }

    fun updateConfirmPassword(value: String) {
        _newPasswordConfirm.value = value
    }

    fun returnToRequestStep() {
        _step.value = PasswordResetStep.RequestCode
        _code.value = ""
        _newPassword.value = ""
        _newPasswordConfirm.value = ""
        _resetCompleted.value = false
        clearMessages()
    }

    fun clearForm() {
        _email.value = ""
        _code.value = ""
        _newPassword.value = ""
        _newPasswordConfirm.value = ""
        _step.value = PasswordResetStep.RequestCode
        _isLoading.value = false
        _resetCompleted.value = false
        clearMessages()
    }

    fun dismissErrorMessage() {
        _showError.value = false
        _errorMessage.value = null
    }

    private fun clearMessages() {
        _showError.value = false
        _errorMessage.value = null
        _infoMessage.value = null
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isBlank()) {
            showError(UiText.StringResource(R.string.common_validation_email_required))
            return false
        }
        if (!EMAIL_REGEX.matches(email)) {
            showError(UiText.StringResource(R.string.common_validation_email_invalid))
            return false
        }
        return true
    }

    private fun showError(message: UiText) {
        _showError.value = true
        _errorMessage.value = message
    }

    private fun ApiException.toRequestErrorMessage(): UiText {
        return when (statusCode) {
            400 -> UiText.StringResource(R.string.common_validation_email_invalid)
            503 -> UiText.StringResource(R.string.password_reset_error_unavailable)
            null -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_server_try_again_later)
        }
    }

    private fun ApiException.toConfirmErrorMessage(): UiText {
        return when (statusCode) {
            400 -> UiText.StringResource(R.string.password_reset_error_invalid_or_expired_code)
            503 -> UiText.StringResource(R.string.password_reset_error_unavailable)
            null -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_server_try_again_later)
        }
    }

    private data class PasswordResetFormValues(
        val email: String,
        val code: String,
        val newPassword: String,
        val newPasswordConfirm: String,
    )

    private data class PasswordResetStatusValues(
        val step: PasswordResetStep,
        val isLoading: Boolean,
        val showError: Boolean,
        val errorMessage: UiText?,
        val infoMessage: UiText?,
        val resetCompleted: Boolean,
    )

    private data class PasswordResetProgressValues(
        val step: PasswordResetStep,
        val isLoading: Boolean,
        val showError: Boolean,
    )

    private data class PasswordResetMessageValues(
        val errorMessage: UiText?,
        val infoMessage: UiText?,
        val resetCompleted: Boolean,
    )

    private companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private val EMAIL_REGEX = Regex(
            pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            option = RegexOption.IGNORE_CASE,
        )
    }
}

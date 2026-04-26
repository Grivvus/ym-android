package sstu.grivvus.ym.passwordReset

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.ServerApiException
import sstu.grivvus.ym.testutil.MainDispatcherRule
import sstu.grivvus.ym.ui.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>()

    @Test
    fun requestResetCode_withBlankEmail_showsValidationError() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("")
        viewModel.requestResetCode()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(UiText.StringResource(R.string.common_validation_email_required))
        assertThat(viewModel.uiState.value.step).isEqualTo(PasswordResetStep.RequestCode)
        coVerify(exactly = 0) { userRepository.requestPasswordReset(any()) }
    }

    @Test
    fun requestResetCode_whenRepositorySucceeds_movesToConfirmStep() = runTest {
        coEvery { userRepository.requestPasswordReset("tester@example.com") } returns "accepted"
        val viewModel = createViewModel()

        viewModel.updateEmail(" tester@example.com ")
        viewModel.requestResetCode()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.step).isEqualTo(PasswordResetStep.ConfirmReset)
        assertThat(viewModel.uiState.value.email).isEqualTo("tester@example.com")
        assertThat(viewModel.uiState.value.infoMessage)
            .isEqualTo(UiText.StringResource(R.string.password_reset_info_code_sent))
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        coVerify(exactly = 1) { userRepository.requestPasswordReset("tester@example.com") }
    }

    @Test
    fun requestResetCode_whenServiceUnavailable_showsUnavailableError() = runTest {
        coEvery {
            userRepository.requestPasswordReset("tester@example.com")
        } throws ServerApiException(statusCode = 503, message = "mail unavailable")
        val viewModel = createViewModel()

        viewModel.updateEmail("tester@example.com")
        viewModel.requestResetCode()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(UiText.StringResource(R.string.password_reset_error_unavailable))
        assertThat(viewModel.uiState.value.step).isEqualTo(PasswordResetStep.RequestCode)
    }

    @Test
    fun confirmPasswordReset_withShortPassword_showsValidationError() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("tester@example.com")
        viewModel.updateCode("123456")
        viewModel.updateNewPassword("12345")
        viewModel.updateConfirmPassword("12345")
        viewModel.confirmPasswordReset()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(UiText.StringResource(R.string.common_validation_password_min_length))
        assertThat(viewModel.uiState.value.resetCompleted).isFalse()
        coVerify(exactly = 0) { userRepository.confirmPasswordReset(any(), any(), any()) }
    }

    @Test
    fun confirmPasswordReset_whenRepositorySucceeds_marksCompleted() = runTest {
        coEvery {
            userRepository.confirmPasswordReset("tester@example.com", "123456", "new-pass")
        } returns "changed"
        val viewModel = createViewModel()

        viewModel.updateEmail(" tester@example.com ")
        viewModel.updateCode(" 123456 ")
        viewModel.updateNewPassword("new-pass")
        viewModel.updateConfirmPassword("new-pass")
        viewModel.confirmPasswordReset()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.resetCompleted).isTrue()
        assertThat(viewModel.uiState.value.infoMessage)
            .isEqualTo(UiText.StringResource(R.string.password_reset_success))
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        coVerify(exactly = 1) {
            userRepository.confirmPasswordReset("tester@example.com", "123456", "new-pass")
        }
    }

    @Test
    fun confirmPasswordReset_whenCodeInvalid_showsInvalidCodeError() = runTest {
        coEvery {
            userRepository.confirmPasswordReset("tester@example.com", "bad-code", "new-pass")
        } throws ClientApiException(statusCode = 400, message = "invalid code")
        val viewModel = createViewModel()

        viewModel.updateEmail("tester@example.com")
        viewModel.updateCode("bad-code")
        viewModel.updateNewPassword("new-pass")
        viewModel.updateConfirmPassword("new-pass")
        viewModel.confirmPasswordReset()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.resetCompleted).isFalse()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(UiText.StringResource(R.string.password_reset_error_invalid_or_expired_code))
    }

    private fun TestScope.createViewModel(): PasswordResetViewModel {
        val viewModel = PasswordResetViewModel(userRepository)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }
}

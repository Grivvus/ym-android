package sstu.grivvus.ym.passwordChange

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordChangeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>()

    @Test
    fun changePassword_withBlankCurrentPassword_showsValidationError() = runTest {
        val viewModel = PasswordChangeViewModel(userRepository)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateCurrentPassword("")
        viewModel.updateNewPassword("123456")
        viewModel.updateConfirmPassword("123456")
        viewModel.changePassword()

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo("current password shouldn't be empty")
        assertThat(viewModel.uiState.value.success).isFalse()
        coVerify(exactly = 0) { userRepository.changePassword(any(), any()) }
    }

    @Test
    fun changePassword_whenRepositorySucceeds_marksSuccess() = runTest {
        coEvery { userRepository.changePassword("old-pass", "new-pass") } returns Unit
        val viewModel = PasswordChangeViewModel(userRepository)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateCurrentPassword("old-pass")
        viewModel.updateNewPassword("new-pass")
        viewModel.updateConfirmPassword("new-pass")
        viewModel.changePassword()

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.success).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        coVerify(exactly = 1) { userRepository.changePassword("old-pass", "new-pass") }
    }

    @Test
    fun changePassword_whenRepositoryFails_showsNetworkError() = runTest {
        coEvery { userRepository.changePassword("old-pass", "new-pass") } throws IOException("boom")
        val viewModel = PasswordChangeViewModel(userRepository)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateCurrentPassword("old-pass")
        viewModel.updateNewPassword("new-pass")
        viewModel.updateConfirmPassword("new-pass")
        viewModel.changePassword()

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.success).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("boom")
    }
}

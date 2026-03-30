package sstu.grivvus.yamusic.serverSetup

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.network.OpenApiNetworkClient
import sstu.grivvus.yamusic.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val serverInfoRepository = mockk<ServerInfoRepository>()
    private val networkClient = mockk<OpenApiNetworkClient>()

    @Test
    fun proceed_withInvalidPort_showsValidationErrorAndSkipsNetwork() = runTest {
        val viewModel = ServerSetupViewModel(serverInfoRepository, networkClient)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateHost("example.com")
        viewModel.updatePort("70000")
        viewModel.proceed { error("onSuccess should not be called") }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showError).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo("Port value must be in range 1..65535")
        coVerify(exactly = 0) { networkClient.ping(any(), any()) }
        coVerify(exactly = 0) { serverInfoRepository.saveServerInfo(any(), any()) }
    }

    @Test
    fun proceed_whenPingSucceeds_savesServerAndInvokesSuccess() = runTest {
        coEvery { networkClient.ping("example.com", 8080) } just runs
        coEvery { serverInfoRepository.saveServerInfo("example.com", "8080") } just runs
        val viewModel = ServerSetupViewModel(serverInfoRepository, networkClient)
        var successCalled = false
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateHost("example.com")
        viewModel.updatePort("8080")
        viewModel.proceed { successCalled = true }

        advanceUntilIdle()

        assertThat(successCalled).isTrue()
        assertThat(viewModel.uiState.value.showError).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        coVerify(exactly = 1) { networkClient.ping("example.com", 8080) }
        coVerify(exactly = 1) { serverInfoRepository.saveServerInfo("example.com", "8080") }
    }

    @Test
    fun proceed_whenPingFails_showsConnectivityError() = runTest {
        coEvery { networkClient.ping("example.com", 8080) } throws IOException("down")
        val viewModel = ServerSetupViewModel(serverInfoRepository, networkClient)
        backgroundScope.launch(mainDispatcherRule.dispatcher) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateHost("example.com")
        viewModel.updatePort("8080")
        viewModel.proceed { error("onSuccess should not be called") }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showError).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo("Unable to connect to server. Check host, port and /ping endpoint")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        coVerify(exactly = 0) { serverInfoRepository.saveServerInfo(any(), any()) }
    }
}

package sstu.grivvus.ym.startup

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import sstu.grivvus.ym.AppDestinations
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.UserRepository
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.ServerInfo
import sstu.grivvus.ym.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val serverInfoRepository = mockk<ServerInfoRepository>()
    private val userRepository = mockk<UserRepository>()

    @Test
    fun init_withoutServerInfo_routesToServerSetup() = runTest {
        coEvery { serverInfoRepository.getServerInfo() } returns null
        coEvery { userRepository.getCurrentUser() } returns null

        val viewModel = StartupViewModel(serverInfoRepository, userRepository)

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.targetRoute).isEqualTo(AppDestinations.SERVER_SETUP_ROUTE)
    }

    @Test
    fun init_withServerInfoButWithoutUser_routesToRegistration() = runTest {
        coEvery { serverInfoRepository.getServerInfo() } returns ServerInfo("example.com", "8080")
        coEvery { userRepository.getCurrentUser() } returns null

        val viewModel = StartupViewModel(serverInfoRepository, userRepository)

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.targetRoute).isEqualTo(AppDestinations.REGISTRATION_ROUTE)
    }

    @Test
    fun init_withServerInfoAndUser_routesToMusic() = runTest {
        coEvery { serverInfoRepository.getServerInfo() } returns ServerInfo("example.com", "8080")
        coEvery { userRepository.getCurrentUser() } returns LocalUser(
            remoteId = 7L,
            username = "tester",
            email = "t@example.com",
            access = "access",
            refresh = "refresh",
        )

        val viewModel = StartupViewModel(serverInfoRepository, userRepository)

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.targetRoute).isEqualTo(AppDestinations.MUSIC_ROUTE)
    }
}

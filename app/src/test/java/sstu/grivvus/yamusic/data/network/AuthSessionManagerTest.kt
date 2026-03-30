package sstu.grivvus.yamusic.data.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.local.AppDatabase
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthSessionManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startSessionAndLogout_updateSessionStateAndStorage() = runTest {
        val serverInfoRepository = createServerInfoRepository(backgroundScope)
        val sessionManager = createSessionManager(serverInfoRepository, backgroundScope)
        val user = testUser()

        advanceUntilIdle()
        sessionManager.startSession(user)

        assertThat(sessionManager.getActiveUser()).isEqualTo(user)
        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Authenticated(user.remoteId))

        sessionManager.logout()

        assertThat(sessionManager.getActiveUser()).isNull()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.LOGOUT))
    }

    @Test
    fun requireActiveUser_withoutStoredSession_marksSessionExpired() = runTest {
        val sessionManager = createSessionManager(
            createServerInfoRepository(backgroundScope),
            backgroundScope,
        )

        advanceUntilIdle()
        val error = try {
            sessionManager.requireActiveUser()
            error("Expected SessionExpiredException")
        } catch (expected: SessionExpiredException) {
            expected
        }

        assertThat(error).hasMessageThat().contains("Session expired")
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
    }

    @Test
    fun refreshAccessTokenAfterUnauthorized_whenRefreshSucceeds_updatesStoredTokens() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "user_id": 1,
                          "token_type": "bearer",
                          "access_token": "new-access",
                          "refresh_token": "new-refresh"
                        }
                        """.trimIndent()
                    )
            )

            val serverInfoRepository = createServerInfoRepository(backgroundScope)
            serverInfoRepository.saveServerInfo(server.hostName, server.port.toString())
            val sessionManager = createSessionManager(serverInfoRepository, backgroundScope)
            sessionManager.startSession(testUser())

            val refreshedAccessToken =
                sessionManager.refreshAccessTokenAfterUnauthorized("old-access")
            val request = server.takeRequest()

            assertThat(refreshedAccessToken).isEqualTo("new-access")
            assertThat(sessionManager.getActiveUser()?.access).isEqualTo("new-access")
            assertThat(sessionManager.getActiveUser()?.refresh).isEqualTo("new-refresh")
            assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Authenticated(1L))
            assertThat(request.path).isEqualTo("/auth/refresh")
            assertThat(request.body.readUtf8()).contains("\"refresh_token\":\"refresh-token\"")
        }
    }

    @Test
    fun refreshAccessTokenAfterUnauthorized_whenRefreshReturns401_expiresSession() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(401).setBody("""{"error":"token has expired"}""")
            )

            val serverInfoRepository = createServerInfoRepository(backgroundScope)
            serverInfoRepository.saveServerInfo(server.hostName, server.port.toString())
            val sessionManager = createSessionManager(serverInfoRepository, backgroundScope)
            sessionManager.startSession(testUser())

            val refreshedAccessToken =
                sessionManager.refreshAccessTokenAfterUnauthorized("old-access")

            assertThat(refreshedAccessToken).isNull()
            assertThat(sessionManager.getActiveUser()).isNull()
            assertThat(sessionManager.sessionState.value)
                .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
        }
    }

    private fun createServerInfoRepository(applicationScope: CoroutineScope): ServerInfoRepository {
        return ServerInfoRepository(
            serverInfoDao = database.serverInfoDao(),
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = applicationScope,
        )
    }

    private fun createSessionManager(
        serverInfoRepository: ServerInfoRepository,
        applicationScope: CoroutineScope,
    ): AuthSessionManager {
        return AuthSessionManager(
            userDao = database.userDao(),
            serverInfoRepository = serverInfoRepository,
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = applicationScope,
        )
    }

    private fun testUser(): LocalUser {
        return LocalUser(
            remoteId = 1L,
            username = "tester",
            email = "tester@example.com",
            access = "old-access",
            refresh = "refresh-token",
        )
    }
}

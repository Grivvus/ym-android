package sstu.grivvus.ym.data.network.auth

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sstu.grivvus.ym.data.local.AppDatabase
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultAuthSessionManagerTest {
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
        val tokenRefresher = FakeTokenRefresher()
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)
        val session = NetworkSession(
            userId = 1L,
            accessToken = "old-access",
            refreshToken = "refresh-token",
        )

        advanceUntilIdle()
        sessionManager.startSession(session)

        assertThat(sessionManager.currentSessionOrNull()).isEqualTo(session)
        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Authenticated(session))

        sessionManager.logout()

        assertThat(sessionManager.currentSessionOrNull()).isNull()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.LOGOUT))
    }

    @Test
    fun requireSession_withoutStoredSession_marksSessionExpired() = runTest {
        val sessionManager = createSessionManager(FakeTokenRefresher(), backgroundScope)

        advanceUntilIdle()
        val error = expectThrows<SessionExpiredException> {
            sessionManager.requireSession()
        }

        assertThat(error).hasMessageThat().contains("Session expired")
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
    }

    @Test
    fun refreshAfterUnauthorized_whenRefreshSucceeds_updatesTokensAndPreservesProfile() = runTest {
        val avatarUri = Uri.parse("http://example.com/users/1/avatar")
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
                avatarUri = avatarUri,
            ),
        )
        val tokenRefresher = FakeTokenRefresher(
            nextSession = NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
        )
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

        advanceUntilIdle()
        val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("old-access")

        assertThat(refreshedAccessToken).isEqualTo("new-access")
        assertThat(tokenRefresher.invocationCount).isEqualTo(1)
        assertThat(tokenRefresher.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(sessionManager.currentSessionOrNull()).isEqualTo(
            NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
        )
        assertThat(database.userDao().getActiveUser()).isEqualTo(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "new-access",
                refresh = "new-refresh",
                avatarUri = avatarUri,
            ),
        )
        assertThat(sessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(
                NetworkSession(
                    userId = 1L,
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            ),
        )
    }

    @Test
    fun refreshAfterUnauthorized_whenRefreshReturns401_expiresSession() = runTest {
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
            ),
        )
        val sessionManager = createSessionManager(
            FakeTokenRefresher(
                refreshError = UnauthorizedApiException(
                    message = "Unauthorized",
                    rawBody = """{"error":"token expired"}""",
                ),
            ),
            backgroundScope,
        )

        advanceUntilIdle()
        val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("old-access")

        assertThat(refreshedAccessToken).isNull()
        assertThat(database.userDao().getActiveUser()).isNull()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
    }

    @Test
    fun refreshAfterUnauthorized_whenTokenWasAlreadyRotated_returnsCurrentTokenWithoutRefresh() =
        runTest {
            database.userDao().insert(
                LocalUser(
                    remoteId = 1L,
                    username = "tester",
                    email = "tester@example.com",
                    access = "fresh-access",
                    refresh = "refresh-token",
                ),
            )
            val tokenRefresher = FakeTokenRefresher(
                nextSession = NetworkSession(
                    userId = 1L,
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            )
            val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

            advanceUntilIdle()
            val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("stale-access")

            assertThat(refreshedAccessToken).isEqualTo("fresh-access")
            assertThat(tokenRefresher.invocationCount).isEqualTo(0)
        }

    @Test
    fun refreshAfterUnauthorized_concurrentCalls_refreshOnlyOnce() = runTest {
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
            ),
        )
        val tokenRefresher = FakeTokenRefresher(
            nextSession = NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
            refreshDelayMs = 50L,
        )
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

        advanceUntilIdle()
        val firstCall = async { sessionManager.refreshAfterUnauthorized("old-access") }
        val secondCall = async { sessionManager.refreshAfterUnauthorized("old-access") }

        advanceUntilIdle()

        assertThat(firstCall.await()).isEqualTo("new-access")
        assertThat(secondCall.await()).isEqualTo("new-access")
        assertThat(tokenRefresher.invocationCount).isEqualTo(1)
    }

    private fun createSessionManager(
        tokenRefresher: FakeTokenRefresher,
        applicationScope: CoroutineScope,
    ): DefaultAuthSessionManager {
        return DefaultAuthSessionManager(
            userDao = database.userDao(),
            audioTrackDao = database.audioTrackDao(),
            albumDao = database.albumDao(),
            artistDao = database.artistDao(),
            playlistDao = database.playlistDao(),
            trackAlbumDao = database.trackAlbumDao(),
            playlistTrackDao = database.playlistTrackDao(),
            tokenRefresher = tokenRefresher,
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = applicationScope,
        )
    }

    private class FakeTokenRefresher(
        private val nextSession: NetworkSession? = null,
        private val refreshError: Throwable? = null,
        private val refreshDelayMs: Long = 0L,
    ) : TokenRefresher {
        var invocationCount: Int = 0
            private set

        var lastRefreshToken: String? = null
            private set

        override suspend fun refresh(refreshToken: String): NetworkSession {
            invocationCount += 1
            lastRefreshToken = refreshToken
            if (refreshDelayMs > 0) {
                delay(refreshDelayMs)
            }
            refreshError?.let { throw it }
            return checkNotNull(nextSession)
        }
    }

    private suspend inline fun <reified T : Throwable> expectThrows(
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) {
                return error
            }
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown")
    }
}

package sstu.grivvus.yamusic.data.network.core

import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.data.network.auth.SessionEndReason
import sstu.grivvus.yamusic.data.network.auth.SessionExpiredException
import sstu.grivvus.yamusic.data.network.auth.SessionRequiredException
import sstu.grivvus.yamusic.data.network.auth.SessionState
import sstu.grivvus.yamusic.data.network.model.NetworkSession
import sstu.grivvus.yamusic.openapi.infrastructure.ApiClient as GeneratedApiClient

@OptIn(ExperimentalCoroutinesApi::class)
class OpenApiGeneratedApiProviderTest {
    @After
    fun tearDown() {
        GeneratedApiClient.accessToken = null
    }

    @Test
    fun withPublicApi_usesConfiguredBaseUrlAndRestoresPreviousToken() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://example.com:8080"),
            authSessionManagerProvider = authSessionManagerProvider("unused"),
        )

        val baseUrl = provider.withPublicApi { api ->
            assertThat(GeneratedApiClient.accessToken).isNull()
            api.baseUrl
        }

        assertThat(baseUrl).isEqualTo("http://example.com:8080")
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    @Test
    fun withAuthorizedApi_setsResolvedAccessTokenForTheDurationOfCall() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local"),
            authSessionManagerProvider = authSessionManagerProvider("fresh-token"),
        )

        val observedToken = provider.withAuthorizedApi { api ->
            assertThat(api.baseUrl).isEqualTo("http://music.local")
            GeneratedApiClient.accessToken
        }

        assertThat(observedToken).isEqualTo("fresh-token")
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    @Test
    fun withAuthorizedApi_withoutActiveToken_throwsSessionRequiredException() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local"),
            authSessionManagerProvider = authSessionManagerProvider(null),
        )

        val error = expectThrows<SessionRequiredException> {
            provider.withAuthorizedApi { error("Should not be called") }
        }

        assertThat(error).isInstanceOf(SessionRequiredException::class.java)
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    @Test
    fun withAuthorizedApi_restoresPreviousTokenAfterFailure() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local"),
            authSessionManagerProvider = authSessionManagerProvider("fresh-token"),
        )

        val error = expectThrows<IllegalStateException> {
            provider.withAuthorizedApi<Unit> {
                throw IllegalStateException("boom")
            }
        }

        assertThat(error.message).isEqualTo("boom")
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    @Test
    fun withAuthorizedApi_onUnauthorized_refreshesTokenAndRetriesOnce() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val authSessionManager = FakeAuthSessionManager(
            accessToken = "stale-token",
            refreshedAccessToken = "fresh-token",
        )
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local"),
            authSessionManagerProvider = Provider { authSessionManager },
        )
        var invocationCount = 0

        val observedToken = provider.withAuthorizedApi { api ->
            invocationCount += 1
            assertThat(api.baseUrl).isEqualTo("http://music.local")
            when (invocationCount) {
                1 -> {
                    assertThat(GeneratedApiClient.accessToken).isEqualTo("stale-token")
                    throw UnauthorizedApiException(message = "expired")
                }

                2 -> GeneratedApiClient.accessToken
                else -> error("Unexpected retry count: $invocationCount")
            }
        }

        assertThat(observedToken).isEqualTo("fresh-token")
        assertThat(invocationCount).isEqualTo(2)
        assertThat(authSessionManager.refreshInvocationCount).isEqualTo(1)
        assertThat(authSessionManager.lastAttemptedAccessToken).isEqualTo("stale-token")
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    @Test
    fun withAuthorizedApi_whenRefreshFails_throwsSessionExpiredException() = runTest {
        GeneratedApiClient.accessToken = "previous-token"
        val authSessionManager = FakeAuthSessionManager(
            accessToken = "stale-token",
            refreshedAccessToken = null,
        )
        val provider = OpenApiGeneratedApiProvider(
            apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local"),
            authSessionManagerProvider = Provider { authSessionManager },
        )

        val error = expectThrows<SessionExpiredException> {
            provider.withAuthorizedApi<Unit> {
                throw UnauthorizedApiException(message = "expired")
            }
        }

        assertThat(error).isInstanceOf(SessionExpiredException::class.java)
        assertThat(authSessionManager.refreshInvocationCount).isEqualTo(1)
        assertThat(authSessionManager.lastAttemptedAccessToken).isEqualTo("stale-token")
        assertThat(GeneratedApiClient.accessToken).isEqualTo("previous-token")
    }

    private class FixedApiBaseUrlProvider(
        private val baseUrl: String,
    ) : ApiBaseUrlProvider {
        override fun baseUrl(): String = baseUrl
    }

    private fun authSessionManagerProvider(accessToken: String?): Provider<AuthSessionManager> {
        return Provider { FakeAuthSessionManager(accessToken = accessToken) }
    }

    private class FakeAuthSessionManager(
        private var accessToken: String?,
        private val refreshedAccessToken: String? = null,
    ) : AuthSessionManager {
        private val internalState = MutableStateFlow<SessionState>(SessionState.Initializing)

        var refreshInvocationCount: Int = 0
            private set

        var lastAttemptedAccessToken: String? = null
            private set

        override val sessionState: StateFlow<SessionState> = internalState

        override suspend fun startSession(session: NetworkSession) {
            accessToken = session.accessToken
            internalState.value = SessionState.Authenticated(session)
        }

        override suspend fun currentSessionOrNull(): NetworkSession? = null

        override suspend fun requireSession(): NetworkSession {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun resolveAccessToken(): String? = accessToken

        override suspend fun refreshAfterUnauthorized(attemptedAccessToken: String?): String? {
            refreshInvocationCount += 1
            lastAttemptedAccessToken = attemptedAccessToken
            accessToken = refreshedAccessToken
            return refreshedAccessToken
        }

        override suspend fun getCurrentUser(): LocalUser? = null

        override suspend fun requireCurrentUser(): LocalUser {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun updateCurrentUser(user: LocalUser) = Unit

        override suspend fun clearSession(reason: SessionEndReason) {
            accessToken = null
            internalState.value = SessionState.Unauthenticated(reason)
        }

        override suspend fun markSessionExpired() {
            clearSession(SessionEndReason.EXPIRED)
        }

        override suspend fun logout() {
            clearSession(SessionEndReason.LOGOUT)
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

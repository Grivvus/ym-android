package sstu.grivvus.ym.data.network.auth

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.ym.data.network.model.NetworkSession

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedMediaInterceptorTest {
    private val interceptor = AuthenticatedMediaInterceptor(
        apiBaseUrlProvider = FixedApiBaseUrlProvider("http://music.local:8000"),
        authHeaderProvider = FixedAuthHeaderProvider("Bearer test-token"),
        authSessionManager = FakeAuthSessionManager(),
    )

    @Test
    fun intercept_protectedBackendRoute_attachesAuthorization() {
        val chain = RecordingChain(
            request = Request.Builder()
                .url("http://music.local:8000/tracks/42/stream")
                .build(),
        )

        interceptor.intercept(chain)

        assertThat(chain.proceededRequests).hasSize(1)
        assertThat(chain.proceededRequests.single().header("Authorization"))
            .isEqualTo("Bearer test-token")
    }

    @Test
    fun intercept_publicBackendRoute_doesNotAttachAuthorization() {
        val chain = RecordingChain(
            request = Request.Builder()
                .url("http://music.local:8000/auth/login")
                .build(),
        )

        interceptor.intercept(chain)

        assertThat(chain.proceededRequests).hasSize(1)
        assertThat(chain.proceededRequests.single().header("Authorization")).isNull()
    }

    @Test
    fun intercept_foreignHost_doesNotAttachAuthorization() {
        val chain = RecordingChain(
            request = Request.Builder()
                .url("http://cdn.example.com/tracks/42/stream")
                .build(),
        )

        interceptor.intercept(chain)

        assertThat(chain.proceededRequests).hasSize(1)
        assertThat(chain.proceededRequests.single().header("Authorization")).isNull()
    }

    private class FixedApiBaseUrlProvider(
        private val baseUrl: String,
    ) : ApiBaseUrlProvider {
        override fun baseUrl(): String = baseUrl
    }

    private class FixedAuthHeaderProvider(
        private val header: String?,
    ) : AuthHeaderProvider {
        override suspend fun authorizationHeaderOrNull(): String? = header

        override fun authorizationHeaderOrNullBlocking(): String? = header
    }

    private class FakeAuthSessionManager : AuthSessionManager {
        private val internalState = MutableStateFlow<SessionState>(SessionState.Initializing)

        override val sessionState: StateFlow<SessionState> = internalState

        override suspend fun startSession(session: NetworkSession) {
            internalState.value = SessionState.Authenticated(session)
        }

        override suspend fun currentSessionOrNull(): NetworkSession? = null

        override suspend fun requireSession(): NetworkSession {
            throw UnsupportedOperationException("Not used in test")
        }

        override suspend fun resolveAccessToken(): String? = null

        override suspend fun refreshAfterUnauthorized(attemptedAccessToken: String?): String? = null

        override suspend fun getCurrentUser(): LocalUser? = null

        override suspend fun requireCurrentUser(): LocalUser {
            throw UnsupportedOperationException("Not used in test")
        }

        override suspend fun updateCurrentUser(user: LocalUser) = Unit

        override suspend fun clearSession(reason: SessionEndReason) {
            internalState.value = SessionState.Unauthenticated(reason)
        }

        override suspend fun markSessionExpired() {
            clearSession(SessionEndReason.EXPIRED)
        }

        override suspend fun logout() {
            clearSession(SessionEndReason.LOGOUT)
        }
    }

    private class RecordingChain(
        private val request: Request,
    ) : Interceptor.Chain {
        val proceededRequests = mutableListOf<Request>()

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceededRequests += request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun call(): Call {
            throw UnsupportedOperationException("Not used in test")
        }

        override fun connection(): Connection? = null

        override fun connectTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = TimeUnit.SECONDS.toMillis(10).toInt()

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}

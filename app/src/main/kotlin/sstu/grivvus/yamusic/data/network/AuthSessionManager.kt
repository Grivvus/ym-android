package sstu.grivvus.yamusic.data.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.di.IoDispatcher
import sstu.grivvus.yamusic.openapi.models.TokenResponse
import sstu.grivvus.yamusic.openapi.models.UpdateTokenRequest
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionManager @Inject constructor(
    private val userDao: UserDao,
    private val serverInfoRepository: ServerInfoRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    companion object {
        private const val JSON_MEDIA_TYPE = "application/json"
        private val refreshMutex = Mutex()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Initializing)

    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        applicationScope.launch(ioDispatcher) {
            syncSessionStateFromStorage()
        }
    }

    suspend fun getActiveUser(): LocalUser? = userDao.getActiveUser()

    suspend fun requireActiveUser(): LocalUser {
        return userDao.getActiveUser() ?: run {
            markSessionExpired()
            throw SessionExpiredException()
        }
    }

    suspend fun startSession(user: LocalUser) {
        userDao.clearTable()
        userDao.insert(user)
        _sessionState.value = user.toSessionState()
    }

    suspend fun updateCurrentUser(user: LocalUser) {
        userDao.update(user)
        _sessionState.value = user.toSessionState()
    }

    suspend fun resolveAccessToken(preferredAccessToken: String?): String? {
        val storedToken = userDao.getActiveUser()?.access?.takeIf { it.isNotBlank() }
        return storedToken ?: preferredAccessToken?.takeIf { it.isNotBlank() }
    }

    suspend fun refreshAccessTokenAfterUnauthorized(attemptedAccessToken: String?): String? {
        return refreshMutex.withLock {
            val localUser = userDao.getActiveUser() ?: run {
                markSessionExpired()
                return@withLock null
            }
            val currentAccessToken = localUser.access?.takeIf { it.isNotBlank() }
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != attemptedAccessToken) {
                return@withLock currentAccessToken
            }

            val refreshToken = localUser.refresh?.takeIf { it.isNotBlank() } ?: run {
                markSessionExpired()
                return@withLock null
            }

            try {
                val refreshedTokens = requestTokenRefresh(refreshToken)
                userDao.updateTokens(
                    id = localUser.remoteId,
                    newAccess = refreshedTokens.accessToken,
                    newRefresh = refreshedTokens.refreshToken,
                )
                _sessionState.value = localUser.toSessionState()
                refreshedTokens.accessToken
            } catch (error: Exception) {
                if (error.httpStatusCodeOrNull() == 401) {
                    markSessionExpired()
                    null
                } else {
                    throw error
                }
            }
        }
    }

    suspend fun logout() {
        clearSession(SessionEndReason.LOGOUT)
    }

    suspend fun markSessionExpired() {
        clearSession(SessionEndReason.EXPIRED)
    }

    fun currentAccessTokenBlocking(preferredAccessToken: String? = null): String? = runBlocking {
        resolveAccessToken(preferredAccessToken)
    }

    fun refreshAccessTokenAfterUnauthorizedBlocking(attemptedAccessToken: String?): String? =
        runBlocking {
            refreshAccessTokenAfterUnauthorized(attemptedAccessToken)
        }

    fun markSessionExpiredBlocking() {
        runBlocking {
            markSessionExpired()
        }
    }

    fun logoutBlocking() {
        runBlocking {
            logout()
        }
    }

    private suspend fun clearSession(reason: SessionEndReason) {
        userDao.clearTable()
        _sessionState.value = SessionState.Unauthenticated(reason)
    }

    private suspend fun syncSessionStateFromStorage() {
        val localUser = userDao.getActiveUser()
        _sessionState.value = localUser?.toSessionState() ?: SessionState.Unauthenticated()
    }

    private suspend fun requestTokenRefresh(refreshToken: String): TokenResponse =
        withContext(ioDispatcher) {
            val path = "/auth/refresh"
            val requestBody = json.encodeToString(UpdateTokenRequest(refreshToken))
                .toRequestBody(JSON_MEDIA_TYPE.toMediaType())
            val request = Request.Builder()
                .url("${defaultBaseUrl()}$path".toHttpUrl())
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.code != 200) {
                    throw IOException(buildHttpErrorMessage(response.code, body))
                }
                json.decodeFromString(TokenResponse.serializer(), body)
            }
        }

    private fun LocalUser.toSessionState(): SessionState.Authenticated {
        return SessionState.Authenticated(remoteId)
    }

    private fun defaultBaseUrl(): String {
        return serverInfoRepository.currentBaseUrl()
    }

    private fun buildHttpErrorMessage(statusCode: Int, body: String?): String {
        val bodyText = body?.trim().orEmpty()
        return if (bodyText.isBlank()) {
            "HTTP $statusCode"
        } else {
            "HTTP $statusCode | $bodyText"
        }
    }
}

package sstu.grivvus.yamusic.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
import sstu.grivvus.yamusic.Settings
import sstu.grivvus.yamusic.data.local.DatabaseProvider
import sstu.grivvus.yamusic.openapi.models.TokenResponse
import sstu.grivvus.yamusic.openapi.models.UpdateTokenRequest
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthSessionManager(
    context: Context,
) {
    companion object {
        private const val JSON_MEDIA_TYPE = "application/json"
        private val refreshMutex = Mutex()
    }

    private val appContext = context.applicationContext
    private val userDao get() = DatabaseProvider.getDB(appContext).userDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun resolveAccessToken(preferredAccessToken: String?): String? {
        val storedToken = userDao.getActiveUser()?.access?.takeIf { it.isNotBlank() }
        return storedToken ?: preferredAccessToken?.takeIf { it.isNotBlank() }
    }

    suspend fun refreshAccessTokenAfterUnauthorized(attemptedAccessToken: String?): String? {
        return refreshMutex.withLock {
            val localUser = userDao.getActiveUser() ?: return@withLock null
            val currentAccessToken = localUser.access?.takeIf { it.isNotBlank() }
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != attemptedAccessToken) {
                return@withLock currentAccessToken
            }

            val refreshToken = localUser.refresh?.takeIf { it.isNotBlank() } ?: run {
                userDao.clearTable()
                return@withLock null
            }

            try {
                val refreshedTokens = requestTokenRefresh(refreshToken)
                userDao.updateTokens(
                    id = localUser.remoteId,
                    newAccess = refreshedTokens.accessToken,
                    newRefresh = refreshedTokens.refreshToken,
                )
                refreshedTokens.accessToken
            } catch (error: Exception) {
                if (error.httpStatusCodeOrNull() == 401) {
                    userDao.clearTable()
                    null
                } else {
                    throw error
                }
            }
        }
    }

    suspend fun logout() {
        userDao.clearTable()
    }

    fun currentAccessTokenBlocking(preferredAccessToken: String? = null): String? = runBlocking {
        resolveAccessToken(preferredAccessToken)
    }

    fun refreshAccessTokenAfterUnauthorizedBlocking(attemptedAccessToken: String?): String? =
        runBlocking {
            refreshAccessTokenAfterUnauthorized(attemptedAccessToken)
        }

    fun logoutBlocking() {
        runBlocking {
            logout()
        }
    }

    private suspend fun requestTokenRefresh(refreshToken: String): TokenResponse =
        withContext(Dispatchers.IO) {
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

    private fun defaultBaseUrl(): String {
        return "http://${Settings.apiHost}:${Settings.apiPort}"
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

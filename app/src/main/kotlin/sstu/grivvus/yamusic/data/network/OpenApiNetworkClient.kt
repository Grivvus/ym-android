package sstu.grivvus.yamusic.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import sstu.grivvus.yamusic.Settings
import sstu.grivvus.yamusic.di.IoDispatcher
import sstu.grivvus.yamusic.openapi.apis.DefaultApi
import sstu.grivvus.yamusic.openapi.infrastructure.ClientError
import sstu.grivvus.yamusic.openapi.infrastructure.Informational
import sstu.grivvus.yamusic.openapi.infrastructure.Redirection
import sstu.grivvus.yamusic.openapi.infrastructure.ServerError
import sstu.grivvus.yamusic.openapi.infrastructure.Success
import sstu.grivvus.yamusic.openapi.models.UserAuth
import sstu.grivvus.yamusic.openapi.models.UserChangePassword
import sstu.grivvus.yamusic.openapi.models.UserUpdate
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenApiNetworkClient @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    data class RemoteUser(
        val id: Long,
        val username: String,
        val email: String?,
    )

    companion object {
        private const val MAX_LOGGED_BODY_CHARS = 2048
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun register(user: NetworkUserCreate): TokenResponse = withContext(ioDispatcher) {
        val payload = UserAuth(username = user.username, password = user.password)
        logRequest("POST", "/auth/register", payload.toLogString())
        try {
            val response = defaultApi().registerWithHttpInfo(payload)
            logResponse("POST", "/auth/register", response.statusCode)
            when (response) {
                is Success -> {
                    response.data?.toNetworkToken()
                        ?: throw IOException("HTTP ${response.statusCode}: empty response body for register")
                }

                is ClientError -> {
                    throw response.toIOException()
                }

                is ServerError -> {
                    throw response.toIOException()
                }

                is Informational -> {
                    throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                }

                is Redirection -> {
                    throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                }

                else -> throw IOException("Unexpected response for register")
            }
        } catch (e: Exception) {
            logException("POST", "/auth/register", e)
            throw e
        }
    }

    suspend fun login(user: NetworkUserLogin): TokenResponse = withContext(ioDispatcher) {
        val payload = UserAuth(username = user.username, password = user.password)
        logRequest("POST", "/auth/login", payload.toLogString())
        try {
            val response = defaultApi().loginWithHttpInfo(payload)
            logResponse("POST", "/auth/login", response.statusCode)
            when (response) {
                is Success -> {
                    response.data?.toNetworkToken()
                        ?: throw IOException("HTTP ${response.statusCode}: empty response body for login")
                }

                is ClientError -> {
                    throw response.toIOException()
                }

                is ServerError -> {
                    throw response.toIOException()
                }

                is Informational -> {
                    throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                }

                is Redirection -> {
                    throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                }

                else -> throw IOException("Unexpected response for login")
            }
        } catch (e: Exception) {
            logException("POST", "/auth/login", e)
            throw e
        }
    }

    suspend fun ping() = withContext(ioDispatcher) {
        logRequest("GET", "/ping", "<empty>")
        try {
            val response = defaultApi().pingWithHttpInfo()
            logResponse("GET", "/ping", response.statusCode)
            when (response) {
                is Success -> {
                    Unit
                }

                is ClientError -> {
                    throw response.toIOException()
                }

                is ServerError -> {
                    throw response.toIOException()
                }

                is Informational -> {
                    throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                }

                is Redirection -> {
                    throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                }
            }
        } catch (e: Exception) {
            logException("GET", "/ping", e)
            throw e
        }
    }

    suspend fun ping(host: String, port: Int) = withContext(ioDispatcher) {
        logRequest("GET", "http://$host:$port/ping", "<empty>")
        try {
            val response = defaultApi(baseUrl = "http://$host:$port").pingWithHttpInfo()
            logResponse("GET", "http://$host:$port/ping", response.statusCode)
            when (response) {
                is Success -> {
                    Unit
                }

                is ClientError -> {
                    throw response.toIOException()
                }

                is ServerError -> {
                    throw response.toIOException()
                }

                is Informational -> {
                    throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                }

                is Redirection -> {
                    throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                }
            }
        } catch (e: Exception) {
            logException("GET", "http://$host:$port/ping", e)
            throw e
        }
    }

    suspend fun getUserById(userId: Long): RemoteUser = withContext(ioDispatcher) {
        val apiUserId = userId.toApiUserId()
        logRequest("GET", "/user/$apiUserId", "<empty>")
        try {
            val response = defaultApi().getUserByIdWithHttpInfo(apiUserId)
            logResponse("GET", "/user/$apiUserId", response.statusCode)
            when (response) {
                is Success -> {
                    val data = response.data
                        ?: throw IOException("HTTP ${response.statusCode}: empty response body for getUserById")
                    RemoteUser(
                        id = data.id.toLong(),
                        username = data.username,
                        email = data.email,
                    )
                }

                is ClientError -> throw response.toIOException()
                is ServerError -> throw response.toIOException()
                is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                else -> throw IOException("Unexpected response for getUserById")
            }
        } catch (e: Exception) {
            logException("GET", "/user/$apiUserId", e)
            throw e
        }
    }

    suspend fun changeUser(userId: Long, newUsername: String, newEmail: String): RemoteUser =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            val payload = UserUpdate(newUsername = newUsername, newEmail = newEmail)
            logRequest("PATCH", "/user/$apiUserId", payload.toLogString())
            try {
                val response = defaultApi().changeUserWithHttpInfo(apiUserId, payload)
                logResponse("PATCH", "/user/$apiUserId", response.statusCode)
                when (response) {
                    is Success -> {
                        val data = response.data
                            ?: throw IOException("HTTP ${response.statusCode}: empty response body for changeUser")

                        if (data is sstu.grivvus.yamusic.openapi.models.UserReturn) {
                            RemoteUser(
                                id = data.id.toLong(),
                                username = data.username,
                                email = data.email,
                            )
                        } else {
                            throw IOException("HTTP ${response.statusCode}: unexpected response type for changeUser: ${data::class.qualifiedName}")
                        }
                    }

                    is ClientError -> throw response.toIOException()
                    is ServerError -> throw response.toIOException()
                    is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                    is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                    else -> throw IOException("Unexpected response for changeUser")
                }
            } catch (e: Exception) {
                logException("PATCH", "/user/$apiUserId", e)
                throw e
            }
        }

    suspend fun changePassword(userId: Long, currentPassword: String, newPassword: String): String =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            val payload =
                UserChangePassword(oldPassword = currentPassword, newPassword = newPassword)
            logRequest("PATCH", "/user/$apiUserId/change_password", payload.toLogString())
            try {
                val response = defaultApi().changePasswordWithHttpInfo(apiUserId, payload)
                logResponse("PATCH", "/user/$apiUserId/change_password", response.statusCode)
                when (response) {
                    is Success -> response.data?.msg
                        ?: throw IOException("HTTP ${response.statusCode}: empty response body for changePassword")

                    is ClientError -> throw response.toIOException()
                    is ServerError -> throw response.toIOException()
                    is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                    is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                    else -> throw IOException("Unexpected response for changePassword")
                }
            } catch (e: Exception) {
                logException("PATCH", "/user/$apiUserId/change_password", e)
                throw e
            }
        }

    suspend fun uploadUserAvatar(userId: Long, avatarFile: File): String =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            logRequest("POST", "/user/$apiUserId/avatar", avatarFile.toLogString())
            try {
                val response = defaultApi().uploadUserAvatarWithHttpInfo(apiUserId, avatarFile)
                logResponse("POST", "/user/$apiUserId/avatar", response.statusCode)
                when (response) {
                    is Success -> response.data?.msg
                        ?: throw IOException("HTTP ${response.statusCode}: empty response body for uploadUserAvatar")

                    is ClientError -> throw response.toIOException()
                    is ServerError -> throw response.toIOException()
                    is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                    is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                    else -> throw IOException("Unexpected response for uploadUserAvatar")
                }
            } catch (e: Exception) {
                logException("POST", "/user/$apiUserId/avatar", e)
                throw e
            }
        }

    private fun defaultApi(): DefaultApi {
        return defaultApi(baseUrl = "http://${Settings.apiHost}:${Settings.apiPort}")
    }

    private fun defaultApi(baseUrl: String): DefaultApi {
        return DefaultApi(basePath = baseUrl, client = httpClient)
    }

    private fun sstu.grivvus.yamusic.openapi.models.TokenResponse.toNetworkToken(): TokenResponse {
        return TokenResponse(
            userId = userId.toLong(),
            tokenType = tokenType,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private fun ClientError<*>.toIOException(): IOException {
        val details = listOfNotNull(
            "HTTP $statusCode".takeIf { statusCode > 0 },
            message?.takeIf { it.isNotBlank() },
            body?.toString()?.takeIf { it.isNotBlank() },
        ).joinToString(" | ")
        return IOException(details.ifBlank { "Client request failed" })
    }

    private fun ServerError<*>.toIOException(): IOException {
        val details = listOfNotNull(
            "HTTP $statusCode".takeIf { statusCode > 0 },
            message?.takeIf { it.isNotBlank() },
            body?.toString()?.takeIf { it.isNotBlank() },
        ).joinToString(" | ")
        return IOException(details.ifBlank { "Server request failed" })
    }

    private fun RequestBody.toLogString(): String {
        val contentType = contentType()?.toString().orEmpty()
        if (contentType.startsWith("multipart/") || contentType.contains("octet-stream")) {
            val length = contentLength().takeIf { it >= 0 }?.toString() ?: "unknown"
            return "<$contentType; $length bytes>"
        }

        return runCatching {
            val buffer = Buffer()
            writeTo(buffer)
            val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val raw = buffer.readString(charset)
            if (raw.length <= MAX_LOGGED_BODY_CHARS) raw else raw.take(MAX_LOGGED_BODY_CHARS) + "..."
        }.getOrElse {
            "<unavailable: ${it::class.simpleName}>"
        }
    }

    private fun logRequest(method: String, path: String, body: String) {
        val message = "$method $path\nRequest body: $body"
        Timber.tag("NetworkRequest").i(message)
        Log.i("NetworkRequest", message)
    }

    private fun logResponse(method: String, path: String, statusCode: Int) {
        val message = "$method $path -> $statusCode"
        Timber.tag("NetworkRequest").i(message)
        Log.i("NetworkRequest", message)
    }

    private fun logException(method: String, path: String, error: Throwable) {
        val message = "$method $path -> EXCEPTION: ${error::class.simpleName}: ${error.message}"
        Timber.tag("NetworkRequest").e(error, message)
        Log.e("NetworkRequest", message, error)
    }

    private fun UserAuth.toLogString(): String {
        return "{\"username\":\"$username\",\"password\":\"***\"}"
    }

    private fun UserUpdate.toLogString(): String {
        return "{\"new_username\":\"$newUsername\",\"new_email\":\"$newEmail\"}"
    }

    private fun UserChangePassword.toLogString(): String {
        return "{\"old_password\":\"***\",\"new_password\":\"***\"}"
    }

    private fun File.toLogString(): String {
        return "<file name=\"$name\" size=${length()} path=\"$absolutePath\">"
    }

    private fun Long.toApiUserId(): Int {
        val id = toInt()
        if (id.toLong() != this) {
            throw IOException("User id is out of Int range: $this")
        }
        return id
    }
}

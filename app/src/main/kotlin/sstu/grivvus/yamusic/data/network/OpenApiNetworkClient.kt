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
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenApiNetworkClient @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
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
}

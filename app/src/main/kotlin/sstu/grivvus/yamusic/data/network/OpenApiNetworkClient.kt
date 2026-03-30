package sstu.grivvus.yamusic.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.di.IoDispatcher
import sstu.grivvus.yamusic.openapi.apis.DefaultApi
import sstu.grivvus.yamusic.openapi.infrastructure.ClientError
import sstu.grivvus.yamusic.openapi.infrastructure.Informational
import sstu.grivvus.yamusic.openapi.infrastructure.Redirection
import sstu.grivvus.yamusic.openapi.infrastructure.ServerError
import sstu.grivvus.yamusic.openapi.infrastructure.Success
import sstu.grivvus.yamusic.openapi.models.PlaylistCreateResponse
import sstu.grivvus.yamusic.openapi.models.PlaylistResponse
import sstu.grivvus.yamusic.openapi.models.PlaylistWithTracksResponse
import sstu.grivvus.yamusic.openapi.models.TokenResponse
import sstu.grivvus.yamusic.openapi.models.TrackMetadata
import sstu.grivvus.yamusic.openapi.models.TrackUploadSuccessResponse
import sstu.grivvus.yamusic.openapi.models.UserAuth
import sstu.grivvus.yamusic.openapi.models.UserChangePassword
import sstu.grivvus.yamusic.openapi.models.UserUpdate
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.openapi.infrastructure.ApiClient as GeneratedApiClient

@Singleton
class OpenApiNetworkClient @Inject constructor(
    private val authSessionManager: AuthSessionManager,
    private val serverInfoRepository: ServerInfoRepository,
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

    private val json = Json { ignoreUnknownKeys = true }
    private val generatedApiMutex = Mutex()

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
                    response.data
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
                    response.data
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

    suspend fun getUserById(userId: Long, accessToken: String?): RemoteUser =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            logRequest("GET", "/users/$apiUserId", "<empty>")
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    val response = withGeneratedApi(resolvedAccessToken) { api ->
                        api.getUserByIdWithHttpInfo(apiUserId)
                    }
                    logResponse("GET", "/users/$apiUserId", response.statusCode)
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
                }
            } catch (e: Exception) {
                logException("GET", "/users/$apiUserId", e)
                throw e
            }
        }

    suspend fun changeUser(
        userId: Long,
        newUsername: String,
        newEmail: String,
        accessToken: String?,
    ): RemoteUser =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            val payload = UserUpdate(newUsername = newUsername, newEmail = newEmail)
            logRequest("PATCH", "/users/$apiUserId", payload.toLogString())
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    val response = withGeneratedApi(resolvedAccessToken) { api ->
                        api.changeUserWithHttpInfo(apiUserId, payload)
                    }
                    logResponse("PATCH", "/users/$apiUserId", response.statusCode)
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
                }
            } catch (e: Exception) {
                logException("PATCH", "/users/$apiUserId", e)
                throw e
            }
        }

    suspend fun changePassword(
        userId: Long,
        currentPassword: String,
        newPassword: String,
        accessToken: String?,
    ): String =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            val payload =
                UserChangePassword(oldPassword = currentPassword, newPassword = newPassword)
            logRequest("PATCH", "/users/$apiUserId/change_password", payload.toLogString())
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    val response = withGeneratedApi(resolvedAccessToken) { api ->
                        api.changePasswordWithHttpInfo(apiUserId, payload)
                    }
                    logResponse("PATCH", "/users/$apiUserId/change_password", response.statusCode)
                    when (response) {
                        is Success -> {
                            when (val data = response.data) {
                                is Unit -> "OK"
                                null -> ""
                                else -> data.toString()
                            }
                        }

                        is ClientError -> throw response.toIOException()
                        is ServerError -> throw response.toIOException()
                        is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                        is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                        else -> throw IOException("Unexpected response for changePassword")
                    }
                }
            } catch (e: Exception) {
                logException("PATCH", "/users/$apiUserId/change_password", e)
                throw e
            }
        }

    suspend fun uploadUserAvatar(userId: Long, avatarFile: File, accessToken: String?): String =
        withContext(ioDispatcher) {
            val apiUserId = userId.toApiUserId()
            logRequest("POST", "/users/$apiUserId/avatar", avatarFile.toLogString())
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    val response = withGeneratedApi(resolvedAccessToken) { api ->
                        api.uploadUserAvatarWithHttpInfo(apiUserId, avatarFile)
                    }
                    logResponse("POST", "/users/$apiUserId/avatar", response.statusCode)
                    when (response) {
                        is Success -> response.data?.msg
                            ?: throw IOException("HTTP ${response.statusCode}: empty response body for uploadUserAvatar")

                        is ClientError -> throw response.toIOException()
                        is ServerError -> throw response.toIOException()
                        is Informational -> throw IOException("HTTP ${response.statusCode}: informational response is not supported")
                        is Redirection -> throw IOException("HTTP ${response.statusCode}: redirection response is not supported")
                        else -> throw IOException("Unexpected response for uploadUserAvatar")
                    }
                }
            } catch (e: Exception) {
                logException("POST", "/users/$apiUserId/avatar", e)
                throw e
            }
        }

    suspend fun getPlaylists(userId: Long, accessToken: String?): List<PlaylistResponse> =
        withContext(ioDispatcher) {
            val path = "/playlists"
            logRequest("GET", path, "<empty>")
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    executeJsonRequest(
                        request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                            .get()
                            .build(),
                        expectedStatuses = setOf(200),
                    )
                }
            } catch (e: Exception) {
                logException("GET", path, e)
                throw e
            }
        }

    suspend fun getPlaylist(
        userId: Long,
        accessToken: String?,
        playlistId: Long,
    ): PlaylistWithTracksResponse = withContext(ioDispatcher) {
        val path = "/playlists/$playlistId"
        logRequest("GET", path, "<empty>")
        try {
            withAuthRetry(accessToken) { resolvedAccessToken ->
                executeJsonRequest(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .get()
                        .build(),
                    expectedStatuses = setOf(200),
                )
            }
        } catch (e: Exception) {
            logException("GET", path, e)
            throw e
        }
    }

    suspend fun updatePlaylist(
        userId: Long,
        accessToken: String?,
        playlistId: Long,
        playlistName: String,
    ): PlaylistWithTracksResponse = withContext(ioDispatcher) {
        val path = "/playlists/$playlistId"
        val requestBody = json.encodeToString(
            PlaylistResponse(
                playlistId = playlistId.toApiIntId(),
                playlistName = playlistName,
            )
        ).toRequestBody("application/json".toMediaType())
        logRequest("PATCH", path, requestBody.toLogString())
        try {
            withAuthRetry(accessToken) { resolvedAccessToken ->
                executeJsonRequest(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .patch(requestBody)
                        .header("Content-Type", "application/json")
                        .build(),
                    expectedStatuses = setOf(200),
                )
            }
        } catch (e: Exception) {
            logException("PATCH", path, e)
            throw e
        }
    }

    suspend fun createPlaylist(
        userId: Long,
        accessToken: String?,
        playlistName: String,
        coverFile: File?,
        coverMimeType: String?,
    ): Long = withContext(ioDispatcher) {
        val path = "/playlists"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("owner_id", userId.toString())
            .addFormDataPart("playlist_name", playlistName)
            .apply {
                if (coverFile != null) {
                    addFormDataPart(
                        "playlist_cover",
                        coverFile.name,
                        coverFile.asRequestBody(resolveMediaType(coverMimeType)),
                    )
                }
            }
            .build()
        logRequest("POST", path, requestBody.toLogString())
        try {
            val response: PlaylistCreateResponse = withAuthRetry(accessToken) { resolvedAccessToken ->
                executeJsonRequest(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .post(requestBody)
                        .build(),
                    expectedStatuses = setOf(200, 201),
                )
            }
            response.playlistId.toLong()
        } catch (e: Exception) {
            logException("POST", path, e)
            throw e
        }
    }

    suspend fun deletePlaylist(userId: Long, accessToken: String?, playlistId: Long) =
        withContext(ioDispatcher) {
            val path = "/playlists/$playlistId"
            logRequest("DELETE", path, "<empty>")
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    executeWithoutBody(
                        request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                            .delete()
                            .build(),
                        expectedStatuses = setOf(200, 204),
                    )
                }
            } catch (e: Exception) {
                logException("DELETE", path, e)
                throw e
            }
        }

    suspend fun addTrackToPlaylist(
        userId: Long,
        accessToken: String?,
        playlistId: Long,
        trackId: Long,
    ) = withContext(ioDispatcher) {
        val path = "/playlists/$playlistId"
        val requestBody = """{"track_id":$trackId}"""
            .toRequestBody("application/json".toMediaType())
        logRequest("POST", path, requestBody.toLogString())
        try {
            withAuthRetry(accessToken) { resolvedAccessToken ->
                executeWithoutBody(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .post(requestBody)
                        .header("Content-Type", "application/json")
                        .build(),
                    expectedStatuses = setOf(200, 201, 204),
                )
            }
        } catch (e: Exception) {
            logException("POST", path, e)
            throw e
        }
    }

    suspend fun uploadPlaylistCover(
        userId: Long,
        accessToken: String?,
        playlistId: Long,
        coverFile: File,
        coverMimeType: String?,
    ) = withContext(ioDispatcher) {
        val path = "/playlists/$playlistId/cover"
        val requestBody = coverFile.asRequestBody(resolveMediaType(coverMimeType))
        logRequest("POST", path, requestBody.toLogString())
        try {
            withAuthRetry(accessToken) { resolvedAccessToken ->
                executeWithoutBody(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .post(requestBody)
                        .header("Content-Type", coverMimeType ?: "image/*")
                        .build(),
                    expectedStatuses = setOf(200, 201),
                )
            }
        } catch (e: Exception) {
            logException("POST", path, e)
            throw e
        }
    }

    suspend fun getTracks(userId: Long, accessToken: String?): List<TrackMetadata> =
        withContext(ioDispatcher) {
            val path = "/tracks"
            logRequest("GET", path, "<empty>")
            try {
                withAuthRetry(accessToken) { resolvedAccessToken ->
                    executeJsonRequest(
                        request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                            .get()
                            .build(),
                        expectedStatuses = setOf(200),
                    )
                }
            } catch (e: Exception) {
                logException("GET", path, e)
                throw e
            }
        }

    suspend fun uploadTrack(
        userId: Long,
        accessToken: String?,
        name: String,
        artistId: Long,
        albumId: Long,
        trackFile: File,
        trackMimeType: String?,
    ): Long = withContext(ioDispatcher) {
        val path = "/tracks"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .addFormDataPart("artist_id", artistId.toString())
            .addFormDataPart("album_id", albumId.toString())
            .addFormDataPart(
                "track",
                trackFile.name,
                trackFile.asRequestBody(resolveMediaType(trackMimeType)),
            )
            .build()
        logRequest("POST", path, requestBody.toLogString())
        try {
            val response: TrackUploadSuccessResponse = withAuthRetry(accessToken) { resolvedAccessToken ->
                executeJsonRequest(
                    request = authenticatedRequestBuilder(path, userId, resolvedAccessToken)
                        .post(requestBody)
                        .build(),
                    expectedStatuses = setOf(200, 201),
                )
            }
            response.trackId.toLong()
        } catch (e: Exception) {
            logException("POST", path, e)
            throw e
        }
    }

    private fun defaultApi(): DefaultApi {
        return defaultApi(baseUrl = serverInfoRepository.currentBaseUrl())
    }

    private fun defaultApi(baseUrl: String): DefaultApi {
        return DefaultApi(basePath = baseUrl, client = httpClient)
    }

    private suspend fun <T> withGeneratedApi(accessToken: String?, block: (DefaultApi) -> T): T {
        return generatedApiMutex.withLock {
            val previousAccessToken = GeneratedApiClient.accessToken
            GeneratedApiClient.accessToken = accessToken?.takeIf { it.isNotBlank() }
            try {
                block(defaultApi())
            } finally {
                GeneratedApiClient.accessToken = previousAccessToken
            }
        }
    }

    private suspend fun <T> withAuthRetry(
        accessToken: String?,
        block: suspend (String?) -> T,
    ): T {
        val resolvedAccessToken = authSessionManager.resolveAccessToken(accessToken)
        val initialError = try {
            return block(resolvedAccessToken)
        } catch (error: Exception) {
            if (error.httpStatusCodeOrNull() != 401) {
                throw error
            }
            error
        }

        val refreshedAccessToken =
            authSessionManager.refreshAccessTokenAfterUnauthorized(resolvedAccessToken)
                ?: throw initialError

        return try {
            block(refreshedAccessToken)
        } catch (retryError: Exception) {
            if (retryError.httpStatusCodeOrNull() == 401) {
                authSessionManager.markSessionExpired()
            }
            throw retryError
        }
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

    private fun Long.toApiIntId(): Int {
        val id = toInt()
        if (id.toLong() != this) {
            throw IOException("Id is out of Int range: $this")
        }
        return id
    }

    private fun authenticatedRequestBuilder(
        path: String,
        userId: Long,
        accessToken: String?,
    ): Request.Builder {
        val url = "${defaultBaseUrl()}$path".toHttpUrl()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
    }

    private fun defaultBaseUrl(): String {
        return serverInfoRepository.currentBaseUrl()
    }

    private fun resolveMediaType(mimeType: String?) =
        (mimeType ?: "application/octet-stream").toMediaType()

    private inline fun <reified T> executeJsonRequest(
        request: Request,
        expectedStatuses: Set<Int>,
    ): T {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            logResponse(request.method, request.url.encodedPath, response.code)
            if (response.code !in expectedStatuses) {
                throw IOException(buildHttpErrorMessage(response.code, body))
            }
            return json.decodeFromString(body)
        }
    }

    private fun executeWithoutBody(
        request: Request,
        expectedStatuses: Set<Int>,
    ) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            logResponse(request.method, request.url.encodedPath, response.code)
            if (response.code !in expectedStatuses) {
                throw IOException(buildHttpErrorMessage(response.code, body))
            }
        }
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

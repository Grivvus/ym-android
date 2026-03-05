package sstu.grivvus.yamusic.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import sstu.grivvus.yamusic.Settings
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

private val networkJson = Json {
    ignoreUnknownKeys = true
}

private val authClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

suspend fun registerUser(user: NetworkUserCreate): TokenResponse {
    return authPost("/auth/register", user)
}

suspend fun loginUser(user: NetworkUserLogin): TokenResponse {
    return authPost("/auth/login", user)
}

private suspend inline fun <reified Req : Any> authPost(
    path: String,
    payload: Req,
): TokenResponse {
    val url = "http://${Settings.apiHost}:${Settings.apiPort}$path"
    val requestBody = networkJson
        .encodeToString(payload)
        .toRequestBody(JSON_MEDIA_TYPE.toMediaType())
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    Timber.tag("Request").i("POST %s", url)

    val response = authClient.newCall(request).await()
    return response.use {
        val responseBody = it.body.string()
        if (!it.isSuccessful) {
            val responsePreview = responseBody.take(200)
            throw IOException("HTTP ${it.code}: $responsePreview")
        }
        if (responseBody.isBlank()) {
            throw IOException("Empty response body")
        }

        try {
            networkJson.decodeFromString<TokenResponse>(responseBody)
        } catch (e: SerializationException) {
            throw IOException("Invalid auth response format", e)
        }
    }
}

private suspend fun okhttp3.Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    if (!continuation.isCancelled) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

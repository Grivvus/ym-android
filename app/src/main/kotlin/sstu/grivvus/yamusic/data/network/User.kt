package sstu.grivvus.yamusic.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import sstu.grivvus.yamusic.Settings
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun registerOrLoginUser(user: NetworkUser, action: String): TokenResponse =
    suspendCancellableCoroutine { continuation ->
        val client = OkHttpClient()
        if (!(action == "login" || action == "register")) {
            throw IllegalArgumentException(
                "Unknown action $action, expected 'login' or 'register'"
            )
        }
        val url = "${Settings.apiHost}:${Settings.apiPort}/auth/${action}/"
        val body = Json.encodeToString(user)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = client.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(IOException("Unexpected code $response"))
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        continuation.resumeWithException(IOException("Response body is null"))
                        return
                    }

                    try {
                        continuation.resume(responseToToken(responseBody))
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        })
    }
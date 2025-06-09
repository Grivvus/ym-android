package sstu.grivvus.yamusic.data.network
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import sstu.grivvus.yamusic.Settings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sstu.grivvus.yamusic.data.local.AudioTrack
import timber.log.Timber
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioNetClient {
    private val client = OkHttpClient()
    private val baseUrl = "http://${Settings.apiHost}${Settings.apiPort}"

    suspend fun uploadTrack(
        file: File,
        title: String,
        artist: String?,
        album: String?,
        callback: (Result<String>) -> Unit
    ): Long? {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            )
            .addFormDataPart("name", title)
        artist?.let {
            requestBody.addFormDataPart("artist", it)
        }
        album?.let {
            requestBody.addFormDataPart("album", it)
        }
        val url = "http://${Settings.apiHost}:${Settings.apiPort}/track/upload/"
        val request = Request.Builder()
            .url(url)
            .post(requestBody.build())
            .build()

        try {
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                throw IOException("Failed to upload: ${response.code}")
            }
            return response.body?.string()?.toLongOrNull()
        } catch (e: Exception) {
            Log.e("DOWNLOAD", "Error: ${e.message}")
            throw e
        }
    }

    suspend fun fetchTracks(): List<RemoteTrackReturn> =
        suspendCancellableCoroutine { continuation ->
            val client = OkHttpClient()
            val url = "http://${Settings.apiHost}:${Settings.apiPort}/track/get_initial/"
            Timber.tag("Request").i("url: $url")
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            Timber.tag("Request").i("Request $request is sent")

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
                            continuation.resume(responseToRemoteTrackReturn(responseBody))
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }

    suspend fun downloadTrack(track: AudioTrack): InputStream? {
        val client = OkHttpClient()
        val url = "http://${Settings.apiHost}:${Settings.apiPort}/track/${track.servId}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                throw IOException("Failed to download: ${response.code}")
            }
            val contentType = response.header("Content-Type") ?: "audio/mpeg"
            return response.body?.byteStream()
        } catch (e: Exception) {
            Log.e("DOWNLOAD", "Error: ${e.message}")
            throw e
        }
    }
}
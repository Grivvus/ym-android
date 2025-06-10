package sstu.grivvus.yamusic.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import sstu.grivvus.yamusic.Settings
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.io.InputStream

suspend fun uploadImage(file: File, username: String) {

    val client = OkHttpClient()
    val url = "http://${Settings.apiHost}:${Settings.apiPort}/user/avatar/$username"
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            name = "avatar",
            filename = "avatar",
            body = file.asRequestBody("image/*".toMediaType())
        )
        .build()

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    try {
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw Exception("Failed to upload image")
        }
    } finally {
        file.delete()
    }
}

suspend fun downloadImage(username: String): InputStream? {
    val client = OkHttpClient()
    val url = "http://${Settings.apiHost}:${Settings.apiPort}/user/avatar/$username"
    val request = Request.Builder()
        .url(url)
        .get()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()

    try {
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            throw IOException("Failed to download: ${response.code}")
        }
        val contentType = response.header("Content-Type") ?: "image/jpeg"
        when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return response.body?.byteStream()
    } catch (e: Exception) {
        Log.e("DOWNLOAD", "Error: ${e.message}")
        throw e
    }
}
package sstu.grivvus.yamusic.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private val pingClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

suspend fun pingServer(host: String, port: Int) {
    val request = Request.Builder()
        .url("http://$host:$port/ping")
        .get()
        .build()

    val response = withContext(Dispatchers.IO) {
        pingClient.newCall(request).execute()
    }

    response.use {
        if (!it.isSuccessful) {
            throw IOException("Ping failed with code ${it.code}")
        }
    }
}

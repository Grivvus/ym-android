package sstu.grivvus.yamusic

import io.github.cdimascio.dotenv.dotenv

import kotlinx.coroutines.flow.SharingStarted

private const val StopTimeoutMillis: Long = 5000
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)


object Settings {
    private val dotenv = dotenv{
        directory = "/assets"
        filename = "env"
    }

    val apiHost = dotenv["API_HOST"] ?: "0.0.0.0"
    val apiPort = dotenv["API_PORT"] ?: "8000"
}

fun getAvatarUrl(username: String): String {
    return "http://${Settings.apiHost}:${Settings.apiPort}/user/avatar/$username"
}
package sstu.grivvus.yamusic

import kotlinx.coroutines.flow.SharingStarted

private const val StopTimeoutMillis: Long = 5000
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)


object Settings {
    @Volatile
    var apiHost: String = "10.0.2.2"
        private set

    @Volatile
    var apiPort: String = "8000"
        private set

    fun configureApi(host: String, port: String) {
        apiHost = host
        apiPort = port
    }
}

fun getAvatarUrl(username: String): String {
    return "http://${Settings.apiHost}:${Settings.apiPort}/user/avatar/$username"
}

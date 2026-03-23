package sstu.grivvus.yamusic

import android.net.Uri
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

fun getAvatarUrl(userId: Long): String {
    return "http://${Settings.apiHost}:${Settings.apiPort}/user/$userId/avatar"
}

fun getPlaylistCoverUrl(playlistId: Long): String {
    return "http://${Settings.apiHost}:${Settings.apiPort}/playlist/$playlistId/cover"
}

fun playlistCoverUri(playlistId: Long, cacheBust: Boolean = false): Uri {
    val url = getPlaylistCoverUrl(playlistId)
    return if (cacheBust) {
        "$url?ts=${System.currentTimeMillis()}".let(Uri::parse)
    } else {
        Uri.parse(url)
    }
}

package sstu.grivvus.ym.data

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.local.ServerInfo
import sstu.grivvus.ym.data.local.ServerInfoDao
import sstu.grivvus.ym.di.ApplicationScope
import sstu.grivvus.ym.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SERVER_HOST = "10.0.2.2"
private const val DEFAULT_SERVER_PORT = "8000"

data class ServerConfig(
    val host: String,
    val port: String,
) {
    fun baseUrl(): String = "http://$host:$port"
}

@Singleton
class ServerInfoRepository @Inject constructor(
    private val serverInfoDao: ServerInfoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private val _persistedServerInfo = MutableStateFlow<ServerInfo?>(null)
    val persistedServerInfo: StateFlow<ServerInfo?> = _persistedServerInfo.asStateFlow()

    @Volatile
    private var currentConfig = ServerConfig(
        host = DEFAULT_SERVER_HOST,
        port = DEFAULT_SERVER_PORT,
    )

    init {
        applicationScope.launch(ioDispatcher) {
            refreshFromStorage()
        }
    }

    suspend fun getServerInfo(): ServerInfo? {
        return _persistedServerInfo.value ?: refreshFromStorage()
    }

    suspend fun saveServerInfo(host: String, port: String) {
        val info = ServerInfo(host = host, port = port)
        withContext(ioDispatcher) {
            serverInfoDao.clear()
            serverInfoDao.insertOrUpdate(info)
        }
        updateCurrentInfo(info)
    }

    fun currentServerConfig(): ServerConfig = currentConfig

    fun currentBaseUrl(): String = currentConfig.baseUrl()

    fun matchesConfiguredServer(host: String, port: Int): Boolean {
        val config = currentConfig
        return config.host == host && config.port == port.toString()
    }

    fun avatarUrl(userId: Long): String {
        return "${currentBaseUrl()}/users/$userId/avatar"
    }

    fun playlistCoverUrl(playlistId: Long): String {
        return "${currentBaseUrl()}/playlists/$playlistId/cover"
    }

    fun playlistCoverUri(playlistId: Long, cacheBust: Boolean = false): Uri {
        val baseUrl = playlistCoverUrl(playlistId)
        return if (cacheBust) {
            Uri.parse("$baseUrl?ts=${System.currentTimeMillis()}")
        } else {
            Uri.parse(baseUrl)
        }
    }

    fun albumCoverUrl(albumId: Long): String {
        return "${currentBaseUrl()}/albums/$albumId/cover"
    }

    fun albumCoverUri(albumId: Long, cacheBust: Boolean = false): Uri {
        val baseUrl = albumCoverUrl(albumId)
        return if (cacheBust) {
            Uri.parse("$baseUrl?ts=${System.currentTimeMillis()}")
        } else {
            Uri.parse(baseUrl)
        }
    }

    fun artistImageUrl(artistId: Long): String {
        return "${currentBaseUrl()}/artists/$artistId/image"
    }

    fun artistImageUri(artistId: Long, cacheBust: Boolean = false): Uri {
        val baseUrl = artistImageUrl(artistId)
        return if (cacheBust) {
            Uri.parse("$baseUrl?ts=${System.currentTimeMillis()}")
        } else {
            Uri.parse(baseUrl)
        }
    }

    private suspend fun refreshFromStorage(): ServerInfo? {
        val storedInfo = withContext(ioDispatcher) {
            serverInfoDao.get()
        }
        updateCurrentInfo(storedInfo)
        return storedInfo
    }

    private fun updateCurrentInfo(info: ServerInfo?) {
        _persistedServerInfo.value = info
        currentConfig = if (info == null) {
            ServerConfig(
                host = DEFAULT_SERVER_HOST,
                port = DEFAULT_SERVER_PORT,
            )
        } else {
            ServerConfig(
                host = info.host,
                port = info.port,
            )
        }
    }
}

package sstu.grivvus.yamusic.data

import sstu.grivvus.yamusic.data.local.ServerInfo
import sstu.grivvus.yamusic.data.local.ServerInfoDao
import javax.inject.Inject

class ServerInfoRepository @Inject constructor(
    private val serverInfoDao: ServerInfoDao,
) {
    suspend fun getServerInfo(): ServerInfo? = serverInfoDao.get()

    suspend fun saveServerInfo(host: String, port: String) {
        serverInfoDao.clear()
        serverInfoDao.insertOrUpdate(ServerInfo(host, port))
    }
}

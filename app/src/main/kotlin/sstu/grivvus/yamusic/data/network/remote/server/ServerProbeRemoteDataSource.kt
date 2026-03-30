package sstu.grivvus.yamusic.data.network.remote.server

interface ServerProbeRemoteDataSource {
    suspend fun ping(host: String, port: Int)
}

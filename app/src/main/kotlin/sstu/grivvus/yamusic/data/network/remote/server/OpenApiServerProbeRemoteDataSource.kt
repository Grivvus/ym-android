package sstu.grivvus.yamusic.data.network.remote.server

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.openapi.apis.DefaultApi

@Singleton
class OpenApiServerProbeRemoteDataSource @Inject constructor(
    private val apiExecutor: ApiExecutor,
) : ServerProbeRemoteDataSource {
    override suspend fun ping(host: String, port: Int) {
        apiExecutor.executeUnit {
            DefaultApi(basePath = "http://$host:$port").pingWithHttpInfo()
        }
    }
}

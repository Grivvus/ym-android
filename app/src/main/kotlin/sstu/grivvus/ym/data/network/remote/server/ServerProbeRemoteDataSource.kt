package sstu.grivvus.ym.data.network.remote.server

import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.openapi.apis.DefaultApi
import javax.inject.Inject
import javax.inject.Singleton

interface ServerProbeRemoteDataSource {
    suspend fun ping(host: String, port: Int)
}

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

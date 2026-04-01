package sstu.grivvus.ym.data.network.core

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.ym.data.ServerInfoRepository

@Singleton
class ServerInfoApiBaseUrlProvider @Inject constructor(
    private val serverInfoRepository: ServerInfoRepository,
) : ApiBaseUrlProvider {
    override fun baseUrl(): String = serverInfoRepository.currentBaseUrl()
}

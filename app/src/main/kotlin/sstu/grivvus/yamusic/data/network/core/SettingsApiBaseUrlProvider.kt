package sstu.grivvus.yamusic.data.network.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsApiBaseUrlProvider @Inject constructor() : ApiBaseUrlProvider {
    override fun baseUrl(): String {
        return TODO("Read and normalize API base URL from settings source")
    }
}

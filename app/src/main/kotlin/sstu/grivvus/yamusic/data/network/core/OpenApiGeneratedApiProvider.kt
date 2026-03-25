package sstu.grivvus.yamusic.data.network.core

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.openapi.apis.DefaultApi

@Singleton
class OpenApiGeneratedApiProvider @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val authSessionManager: AuthSessionManager,
) : GeneratedApiProvider {
    override suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T {
        return TODO("Create generated API instance without auth and execute block")
    }

    override suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T {
        return TODO("Create generated API instance with auth session and execute block")
    }
}

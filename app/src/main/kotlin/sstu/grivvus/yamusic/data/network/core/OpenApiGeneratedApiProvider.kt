package sstu.grivvus.yamusic.data.network.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.data.network.auth.SessionRequiredException
import sstu.grivvus.yamusic.openapi.apis.DefaultApi
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import sstu.grivvus.yamusic.openapi.infrastructure.ApiClient as GeneratedApiClient

@Singleton
class OpenApiGeneratedApiProvider @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val authSessionManagerProvider: Provider<AuthSessionManager>,
) : GeneratedApiProvider {
    private val generatedApiMutex = Mutex()

    override suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T {
        return withGeneratedApi(accessToken = null, block = block)
    }

    override suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T {
        val accessToken = authSessionManagerProvider.get()
            .resolveAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw SessionRequiredException()
        return withGeneratedApi(accessToken = accessToken, block = block)
    }

    private suspend fun <T> withGeneratedApi(
        accessToken: String?,
        block: suspend (DefaultApi) -> T,
    ): T {
        return generatedApiMutex.withLock {
            val previousAccessToken = GeneratedApiClient.accessToken
            GeneratedApiClient.accessToken = accessToken
            try {
                block(DefaultApi(basePath = apiBaseUrlProvider.baseUrl()))
            } finally {
                GeneratedApiClient.accessToken = previousAccessToken
            }
        }
    }
}

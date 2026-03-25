package sstu.grivvus.yamusic.data.network.remote.auth

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.AuthApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkSession

@Singleton
class OpenApiAuthRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val authApiMapper: AuthApiMapper,
) : AuthRemoteDataSource {
    override suspend fun login(username: String, password: String): NetworkSession {
        return TODO("Implement login via generated OpenAPI client")
    }

    override suspend fun register(username: String, email: String?, password: String): NetworkSession {
        return TODO("Implement registration via generated OpenAPI client")
    }

    override suspend fun refresh(refreshToken: String): NetworkSession {
        return TODO("Implement token refresh via generated OpenAPI client")
    }
}

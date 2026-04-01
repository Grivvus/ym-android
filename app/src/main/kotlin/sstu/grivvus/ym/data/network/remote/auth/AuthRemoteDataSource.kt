package sstu.grivvus.ym.data.network.remote.auth

import sstu.grivvus.ym.data.network.auth.TokenRefresher
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.AuthApiMapper
import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.openapi.models.UpdateTokenRequest
import sstu.grivvus.ym.openapi.models.UserAuth
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRemoteDataSource {
    suspend fun login(username: String, password: String): NetworkSession

    suspend fun register(username: String, email: String?, password: String): NetworkSession

    suspend fun refresh(refreshToken: String): NetworkSession
}

@Singleton
class OpenApiAuthRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val authApiMapper: AuthApiMapper,
) : AuthRemoteDataSource, TokenRefresher {
    override suspend fun login(username: String, password: String): NetworkSession {
        return generatedApiProvider.withPublicApi { api ->
            authApiMapper.mapSession(
                apiExecutor.execute {
                    api.loginWithHttpInfo(
                        userAuth = UserAuth(
                            username = username,
                            password = password,
                        ),
                    )
                },
            )
        }
    }

    override suspend fun register(
        username: String,
        email: String?,
        password: String
    ): NetworkSession {
        return generatedApiProvider.withPublicApi { api ->
            authApiMapper.mapSession(
                apiExecutor.execute {
                    api.registerWithHttpInfo(
                        userAuth = UserAuth(
                            username = username,
                            password = password,
                        ),
                    )
                },
            )
        }
    }

    override suspend fun refresh(refreshToken: String): NetworkSession {
        return generatedApiProvider.withPublicApi { api ->
            authApiMapper.mapSession(
                apiExecutor.execute {
                    api.refreshTokensWithHttpInfo(
                        updateTokenRequest = UpdateTokenRequest(refreshToken = refreshToken),
                    )
                },
            )
        }
    }
}

package sstu.grivvus.yamusic.data.network.remote.auth

import sstu.grivvus.yamusic.data.network.auth.TokenRefresher
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.AuthApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkSession
import sstu.grivvus.yamusic.openapi.models.UpdateTokenRequest
import sstu.grivvus.yamusic.openapi.models.UserAuth
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
                    Timber.tag("NETWORK").i("login request sent")
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

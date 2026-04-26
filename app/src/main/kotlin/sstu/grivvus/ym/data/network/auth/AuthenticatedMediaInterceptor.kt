package sstu.grivvus.ym.data.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import sstu.grivvus.ym.data.network.core.ApiBaseUrlProvider
import javax.inject.Inject

class AuthenticatedMediaInterceptor @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val authHeaderProvider: AuthHeaderProvider,
    private val authSessionManager: AuthSessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!shouldAttachAuthorization(request)) {
            return chain.proceed(request)
        }

        val initialAuthorization = request.header(AUTHORIZATION_HEADER)
            ?: authHeaderProvider.authorizationHeaderOrNullBlocking()
        val initialRequest = request.withAuthorization(initialAuthorization)
        val initialResponse = chain.proceed(initialRequest)
        if (initialResponse.code != 401) {
            return initialResponse
        }

        val refreshedAuthorization = try {
            runBlocking {
                authSessionManager.refreshAfterUnauthorized(
                    attemptedAccessToken = initialAuthorization.bearerTokenOrNull(),
                )
            }?.let { "Bearer $it" }
        } catch (e: Exception) {
            initialResponse.close()
            throw e
        } ?: return initialResponse

        initialResponse.close()
        val retryResponse = chain.proceed(request.withAuthorization(refreshedAuthorization))
        if (retryResponse.code == 401) {
            runBlocking {
                authSessionManager.markSessionExpired()
            }
        }
        return retryResponse
    }

    private fun shouldAttachAuthorization(request: Request): Boolean {
        val configuredBaseUrl = apiBaseUrlProvider.baseUrl().toHttpUrlOrNull() ?: return false
        if (request.url.host != configuredBaseUrl.host || request.url.port != configuredBaseUrl.port) {
            return false
        }

        val path = request.url.encodedPath
        return PUBLIC_PATHS.none { it.matches(path) }
    }

    private fun Request.withAuthorization(authorizationHeader: String?): Request {
        if (authorizationHeader.isNullOrBlank()) {
            return this
        }
        return newBuilder()
            .header(AUTHORIZATION_HEADER, authorizationHeader)
            .build()
    }

    private fun String?.bearerTokenOrNull(): String? {
        return this
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        val PUBLIC_PATHS = listOf(
            Regex("^/ping$"),
            Regex("^/auth/login$"),
            Regex("^/auth/register$"),
            Regex("^/auth/refresh$"),
            Regex("^/auth/password-reset/request$"),
            Regex("^/auth/password-reset/confirm$"),
        )
    }
}

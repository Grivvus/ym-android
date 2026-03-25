package sstu.grivvus.yamusic

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import sstu.grivvus.yamusic.data.network.AuthSessionManager

class AuthenticatedApiMediaInterceptor(
    private val context: Context,
) : Interceptor {
    private val authSessionManager = AuthSessionManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!shouldAttachAccessToken(request)) {
            return chain.proceed(request)
        }

        val initialAccessToken = request.bearerTokenOrNull()
            ?: authSessionManager.currentAccessTokenBlocking()
        val authenticatedRequest = request.withBearerToken(initialAccessToken)
        val initialResponse = chain.proceed(authenticatedRequest)
        if (initialResponse.code != 401) {
            return initialResponse
        }

        val refreshedAccessToken = try {
            authSessionManager.refreshAccessTokenAfterUnauthorizedBlocking(initialAccessToken)
        } catch (error: Exception) {
            initialResponse.close()
            throw error
        } ?: return initialResponse

        initialResponse.close()
        val retryResponse = chain.proceed(request.withBearerToken(refreshedAccessToken))
        if (retryResponse.code == 401) {
            authSessionManager.logoutBlocking()
        }
        return retryResponse
    }

    private fun shouldAttachAccessToken(request: Request): Boolean {
        if (request.url.host != Settings.apiHost || request.url.port.toString() != Settings.apiPort) {
            return false
        }

        val path = request.url.encodedPath
        return USER_AVATAR_PATH.matches(path) ||
                PLAYLIST_COVER_PATH.matches(path) ||
                ARTIST_IMAGE_PATH.matches(path) ||
                ALBUM_COVER_PATH.matches(path)
    }

    private fun Request.withBearerToken(accessToken: String?): Request {
        if (accessToken.isNullOrBlank()) {
            return this
        }
        return newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun Request.bearerTokenOrNull(): String? {
        val headerValue = header("Authorization").orEmpty()
        return headerValue.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() }
    }

    private companion object {
        val USER_AVATAR_PATH = Regex("^/users/\\d+/avatar$")
        val PLAYLIST_COVER_PATH = Regex("^/playlists/\\d+/cover$")
        val ARTIST_IMAGE_PATH = Regex("^/artists/\\d+/image$")
        val ALBUM_COVER_PATH = Regex("^/albums/\\d+/cover$")
    }
}

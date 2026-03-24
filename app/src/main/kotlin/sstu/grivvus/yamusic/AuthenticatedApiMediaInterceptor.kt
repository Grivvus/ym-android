package sstu.grivvus.yamusic

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import sstu.grivvus.yamusic.data.local.DatabaseProvider

class AuthenticatedApiMediaInterceptor(
    private val context: Context,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!shouldAttachAccessToken(request)) {
            return chain.proceed(request)
        }

        val accessToken = runBlocking {
            DatabaseProvider.getDB(context).userDao().getActiveUser()?.access
        }?.takeIf { it.isNotBlank() }

        if (accessToken == null || request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
    }

    private fun shouldAttachAccessToken(request: okhttp3.Request): Boolean {
        if (request.url.host != Settings.apiHost || request.url.port.toString() != Settings.apiPort) {
            return false
        }

        val path = request.url.encodedPath
        return USER_AVATAR_PATH.matches(path) ||
                PLAYLIST_COVER_PATH.matches(path) ||
                ARTIST_IMAGE_PATH.matches(path) ||
                ALBUM_COVER_PATH.matches(path)
    }

    private companion object {
        val USER_AVATAR_PATH = Regex("^/users/\\d+/avatar$")
        val PLAYLIST_COVER_PATH = Regex("^/playlists/\\d+/cover$")
        val ARTIST_IMAGE_PATH = Regex("^/artists/\\d+/image$")
        val ALBUM_COVER_PATH = Regex("^/albums/\\d+/cover$")
    }
}

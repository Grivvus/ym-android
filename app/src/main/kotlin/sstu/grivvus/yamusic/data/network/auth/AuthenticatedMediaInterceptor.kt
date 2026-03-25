package sstu.grivvus.yamusic.data.network.auth

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class AuthenticatedMediaInterceptor @Inject constructor(
    private val authHeaderProvider: AuthHeaderProvider,
    private val authSessionManager: AuthSessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return TODO("Attach auth headers for protected media routes and handle session expiration")
    }
}

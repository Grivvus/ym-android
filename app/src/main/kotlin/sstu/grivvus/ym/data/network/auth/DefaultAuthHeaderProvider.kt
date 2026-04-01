package sstu.grivvus.ym.data.network.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class DefaultAuthHeaderProvider @Inject constructor(
    private val authSessionManager: AuthSessionManager,
) : AuthHeaderProvider {
    override suspend fun authorizationHeaderOrNull(): String? {
        return authSessionManager.resolveAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }
    }

    override fun authorizationHeaderOrNullBlocking(): String? = runBlocking {
        authorizationHeaderOrNull()
    }
}

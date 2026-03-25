package sstu.grivvus.yamusic.data.network.auth

import sstu.grivvus.yamusic.data.network.model.NetworkSession

interface AuthSessionManager : SessionStateProvider {
    suspend fun startSession(session: NetworkSession)

    suspend fun currentSessionOrNull(): NetworkSession?

    suspend fun requireSession(): NetworkSession

    suspend fun resolveAccessToken(): String?

    suspend fun refreshAfterUnauthorized(attemptedAccessToken: String?): String?

    suspend fun clearSession(reason: SessionEndReason)

    suspend fun markSessionExpired()

    suspend fun logout()
}

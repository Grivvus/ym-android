package sstu.grivvus.yamusic.data.network.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sstu.grivvus.yamusic.data.network.model.NetworkSession

@Singleton
class DefaultAuthSessionManager @Inject constructor() : AuthSessionManager {
    private val internalState = MutableStateFlow<SessionState>(SessionState.Initializing)

    override val sessionState: StateFlow<SessionState> = internalState.asStateFlow()

    override suspend fun startSession(session: NetworkSession) {
        TODO("Persist session and publish authenticated state")
    }

    override suspend fun currentSessionOrNull(): NetworkSession? {
        return TODO("Read session from persistence")
    }

    override suspend fun requireSession(): NetworkSession {
        return TODO("Return active session or throw a typed session exception")
    }

    override suspend fun resolveAccessToken(): String? {
        return TODO("Resolve access token from the active session")
    }

    override suspend fun refreshAfterUnauthorized(attemptedAccessToken: String?): String? {
        return TODO("Refresh session after HTTP 401 and return a new access token")
    }

    override suspend fun clearSession(reason: SessionEndReason) {
        TODO("Clear persisted session and publish unauthenticated state")
    }

    override suspend fun markSessionExpired() {
        TODO("Clear session with EXPIRED reason")
    }

    override suspend fun logout() {
        TODO("Clear session with LOGOUT reason")
    }
}

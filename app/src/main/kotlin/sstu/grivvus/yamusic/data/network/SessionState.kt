package sstu.grivvus.yamusic.data.network

import java.io.IOException

enum class SessionEndReason {
    LOGOUT,
    EXPIRED,
}

sealed interface SessionState {
    data object Initializing : SessionState

    data class Authenticated(
        val userId: Long,
    ) : SessionState

    data class Unauthenticated(
        val reason: SessionEndReason? = null,
    ) : SessionState
}

class SessionExpiredException : IOException("Session expired")

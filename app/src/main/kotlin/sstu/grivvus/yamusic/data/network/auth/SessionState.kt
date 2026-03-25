package sstu.grivvus.yamusic.data.network.auth

import kotlinx.coroutines.flow.StateFlow
import sstu.grivvus.yamusic.data.network.model.NetworkSession

enum class SessionEndReason {
    LOGOUT,
    EXPIRED,
}

sealed interface SessionState {
    data object Initializing : SessionState

    data class Authenticated(
        val session: NetworkSession,
    ) : SessionState

    data class Unauthenticated(
        val reason: SessionEndReason? = null,
    ) : SessionState
}

interface SessionStateProvider {
    val sessionState: StateFlow<SessionState>
}

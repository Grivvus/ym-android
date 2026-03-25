package sstu.grivvus.yamusic

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import sstu.grivvus.yamusic.data.network.AuthSessionManager
import sstu.grivvus.yamusic.data.network.SessionState
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    authSessionManager: AuthSessionManager,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = authSessionManager.sessionState
}

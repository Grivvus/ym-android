package sstu.grivvus.ym

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.auth.SessionState
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    authSessionManager: AuthSessionManager,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = authSessionManager.sessionState
}

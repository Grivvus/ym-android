package sstu.grivvus.ym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.auth.SessionState
import sstu.grivvus.ym.playback.artwork.PlaybackArtworkCache
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    authSessionManager: AuthSessionManager,
    private val playbackArtworkCache: PlaybackArtworkCache,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = authSessionManager.sessionState

    init {
        viewModelScope.launch {
            sessionState.collect { state ->
                if (state is SessionState.Unauthenticated) {
                    playbackArtworkCache.clear()
                }
            }
        }
    }
}

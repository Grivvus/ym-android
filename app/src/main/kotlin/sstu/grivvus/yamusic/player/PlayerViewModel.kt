package sstu.grivvus.yamusic.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

//sealed class PlayerState {
//    object Initial : PlayerState()
//    object Buffering : PlayerState()
//    object Ended : PlayerState()
//    data class Ready(
//        val isPlaying: Boolean,
//        val duration: Long,
//        val position: Long
//    ) : PlayerState()
//}
//
//
//class PlayerViewModel constructor(
//    context: Context,
//) : ViewModel() {
//    val player: ExoPlayer = ExoPlayer.Builder(context).build()
//    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Initial)
//    val uiState: StateFlow<PlayerState> = _playerState
//
//    init {
//        player.addListener(object : Player.Listener {
//            override fun onPlaybackStateChanged(playbackState: Int) {
//                when (playbackState) {
//                    Player.STATE_READY -> _playerState.value = PlayerState.Ready(
//                        isPlaying = player.isPlaying,
//                        duration = player.duration,
//                        position = player.currentPosition
//                    )
//                    Player.STATE_BUFFERING -> _playerState.value = PlayerState.Buffering
//                    Player.STATE_ENDED -> _playerState.value = PlayerState.Ended
//                }
//            }
//        })
//    }
//
//    fun loadAudio(uri: String) {
//        viewModelScope.launch {
//            val mediaItem = MediaItem.fromUri(uri)
//            player.setMediaItem(mediaItem)
//            player.prepare()
//        }
//    }
//
//    fun playPause() {
//        if (player.isPlaying) {
//            player.pause()
//        } else {
//            player.play()
//        }
//    }
//
//    fun seekTo(position: Long) {
//        player.seekTo(position)
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        player.release()
//    }
//}
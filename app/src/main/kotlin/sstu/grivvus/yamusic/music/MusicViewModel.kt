package sstu.grivvus.yamusic.music

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sstu.grivvus.yamusic.WhileUiSubscribed
import sstu.grivvus.yamusic.data.AudioRepository
import sstu.grivvus.yamusic.data.local.AudioTrack
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class MusicUiState(
    val tracks: List<AudioTrack> = listOf(),
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: AudioRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val context = context
    private val _tracks: MutableStateFlow<List<AudioTrack>>
       = MutableStateFlow(listOf())
    private val _currentTrack: MutableStateFlow<AudioTrack?>
        = MutableStateFlow(null)
    private val _isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val uiState: StateFlow<MusicUiState> =
        combine(
            _tracks, _currentTrack, _isPlaying
        ) {
                tracks, currentTrack, isPlaying ->
            MusicUiState(tracks, currentTrack, isPlaying)
        }.stateIn(viewModelScope, WhileUiSubscribed, MusicUiState())

    val player by lazy {
        Log.i("PLAYER", "player instantiation")
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isPlaying.value = playbackState == Player.STATE_READY && isPlaying
                }
            })
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i("MusicViewModel", "music viewModel init")
            val testTracks = repository.getAllInitialTracks()
            Log.i("MusicViewModel", testTracks.toString())
            _tracks.update { currentList ->
                testTracks
            }
            Log.i("MusicViewModel", "init finished")
        }
    }

    fun playTrack(track: AudioTrack) {
        viewModelScope.launch {
            try {
                val localTrack = if (!track.isDownloaded) {
                    repository.downloadTrack(track)
                } else {
                    track
                }
                _currentTrack.value = localTrack
                val mediaItem: MediaItem
                if (localTrack.localPath != null)
                    mediaItem = MediaItem.fromUri(localTrack.localPath)
                else
                    mediaItem = MediaItem.fromUri(localTrack.uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                _isPlaying.value = true
            } catch (e: Exception) {
                Log.e("Player", "Playback error", e)
                try {
                    _currentTrack.value = track
                    player.setMediaItem(MediaItem.fromUri(track.uri))
                    player.prepare()
                    player.play()
                    _isPlaying.value = true
                } catch (fallbackEx: Exception) {
                    Log.e("Player", "Fallback playback failed", fallbackEx)
                }
            }
        }    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.play()
            _isPlaying.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    fun uploadTrack(
        uri: Uri, title: String,
        artist: String? = "unknown", album: String? = "unknown"
    ) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.cacheDir, "temp.track")
                file.outputStream().use { output ->
                    inputStream?.copyTo(output) ?: ""
                }
                repository.uploadTrack(file, title, artist, album) { result ->
                    result.onSuccess {
                        {}
                    }.onFailure { e ->
                        Log.e("Upload", "Failed to upload", e)
                    }
                }
                inputStream?.close()
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun getFileFromUri(context: Context, uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("upload_", ".mp3", context.cacheDir)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } catch (e: Exception) {
                null
            }
        }
    }

    fun loadRemoteTracks() {
    }
}
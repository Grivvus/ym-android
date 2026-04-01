package sstu.grivvus.ym.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.data.network.remote.stream.StreamingRemoteDataSource
import sstu.grivvus.ym.di.ApplicationScope
import sstu.grivvus.ym.playback.model.PlayableTrack
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.model.PlaybackUiState
import sstu.grivvus.ym.playback.model.PlaybackSource
import sstu.grivvus.ym.playback.service.PlaybackService
import java.io.File

@Singleton
class MediaSessionPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val streamingRemoteDataSource: StreamingRemoteDataSource,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : PlaybackController {
    private val internalState = MutableStateFlow(PlaybackUiState())
    private val controllerMutex = Mutex()
    private var mediaController: MediaController? = null
    private var positionUpdateJob: Job? = null

    override val playbackState: StateFlow<PlaybackUiState> = internalState.asStateFlow()

    override suspend fun play(queue: PlaybackQueue) {
        if (queue.items.isEmpty()) {
            return
        }
        onControllerThread {
            val controller = getController()
            val mediaItems = queue.items.map(::toMediaItem)
            val startIndex = queue.startIndex.coerceIn(0, mediaItems.lastIndex)
            controller.setMediaItems(mediaItems, startIndex, queue.startPositionMs)
            controller.prepare()
            controller.play()
            updateState(controller)
        }
    }

    override suspend fun play(track: PlayableTrack) {
        play(
            PlaybackQueue(
                source = PlaybackSource.SingleTrack(track.id),
                items = listOf(track),
                startIndex = 0,
            ),
        )
    }

    override suspend fun pause() {
        onControllerThread {
            val controller = getController()
            controller.pause()
            updateState(controller)
        }
    }

    override suspend fun resume() {
        onControllerThread {
            val controller = getController()
            controller.play()
            updateState(controller)
        }
    }

    override suspend fun togglePlayback() {
        onControllerThread {
            val controller = getController()
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            updateState(controller)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        onControllerThread {
            val controller = getController()
            controller.seekTo(positionMs.coerceAtLeast(0L))
            updateState(controller)
        }
    }

    override suspend fun skipToNext() {
        onControllerThread {
            val controller = getController()
            controller.seekToNextMediaItem()
            updateState(controller)
        }
    }

    override suspend fun skipToPrevious() {
        onControllerThread {
            val controller = getController()
            controller.seekToPreviousMediaItem()
            updateState(controller)
        }
    }

    private suspend fun getController(): MediaController {
        mediaController?.let { return it }
        return controllerMutex.withLock {
            mediaController?.let { return@withLock it }
            buildController().also { controller ->
                mediaController = controller
                controller.addListener(playerListener)
                updateState(controller)
                startPositionUpdates(controller)
            }
        }
    }

    private suspend fun buildController(): MediaController {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        return suspendCancellableCoroutine { continuation ->
            controllerFuture.addListener(
                {
                    runCatching { controllerFuture.get() }
                        .onSuccess { controller -> continuation.resume(controller) }
                        .onFailure { error -> continuation.resumeWithException(error) }
                },
                context.mainExecutor,
            )
            continuation.invokeOnCancellation {
                controllerFuture.cancel(true)
            }
        }
    }

    private fun startPositionUpdates(controller: MediaController) {
        if (positionUpdateJob != null) {
            return
        }
        positionUpdateJob = applicationScope.launch(Dispatchers.Main.immediate) {
            while (true) {
                updateState(controller)
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun updateState(player: Player) {
        val queue = buildQueue(player)
        val currentIndex = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentTrack = queue.getOrNull(currentIndex)
        val metadataDurationMs = player.currentMediaItem?.mediaMetadata?.durationMs
            ?.takeIf { it != C.TIME_UNSET && it > 0L }
        val resolvedDurationMs = player.duration
            .takeIf { it != C.TIME_UNSET && it >= 0L }
            ?: currentTrack?.durationMs
            ?: metadataDurationMs
            ?: 0L
        internalState.value = PlaybackUiState(
            isConnected = true,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            isPlaying = player.isPlaying,
            isSeekable = currentTrack != null && player.isCurrentMediaItemSeekable,
            currentTrack = currentTrack,
            queue = queue,
            currentIndex = currentIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            durationMs = resolvedDurationMs,
        )
    }

    private fun buildQueue(player: Player): List<PlayableTrack> {
        return List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).toPlayableTrack()
        }
    }

    private fun toMediaItem(track: PlayableTrack): MediaItem {
        val mediaUri = resolvePlaybackUri(track)
        val extras = Bundle().apply {
            putLong(EXTRA_TRACK_ID, track.id)
            putString(EXTRA_SUBTITLE, track.subtitle)
            putString(EXTRA_ARTWORK_URI, track.artworkUri?.toString())
            putLong(EXTRA_DURATION_MS, track.durationMs ?: 0L)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.subtitle)
            .setArtworkUri(track.artworkUri)
            .setDurationMs(track.durationMs)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun resolvePlaybackUri(track: PlayableTrack): Uri {
        return resolvePlaybackDescriptor(track).uri
    }

    private fun resolvePlaybackDescriptor(track: PlayableTrack): PlaybackDataSourceDescriptor {
        val localFile = track.localPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf(File::exists)
        if (localFile != null) {
            return PlaybackDataSourceDescriptor(
                uri = Uri.fromFile(localFile),
                isLocalFile = true,
            )
        }

        val preferredQuality = QUALITY_PREFERENCE.firstNotNullOfOrNull { quality ->
            track.qualityUris[quality]?.let { quality to it }
        } ?: track.qualityUris.entries.firstOrNull()?.toPair()

        val uriFromPreset = preferredQuality?.second
        if (uriFromPreset != null && !uriFromPreset.scheme.isNullOrBlank()) {
            return PlaybackDataSourceDescriptor(
                uri = uriFromPreset,
            )
        }

        val qualityParam = preferredQuality?.first?.toQueryValue()
        return PlaybackDataSourceDescriptor(
            uri = streamingRemoteDataSource.streamUrl(track.id, qualityParam).toUri(),
        )
    }

    private fun MediaItem.toPlayableTrack(): PlayableTrack {
        val metadata = mediaMetadata
        val extras = metadata.extras
        val trackId = when {
            extras?.containsKey(EXTRA_TRACK_ID) == true -> extras.getLong(EXTRA_TRACK_ID)
            else -> mediaId.toLongOrNull() ?: -1L
        }
        val artworkUri = extras?.getString(EXTRA_ARTWORK_URI)?.toUri() ?: metadata.artworkUri
        val durationMs = extras
            ?.takeIf { it.containsKey(EXTRA_DURATION_MS) }
            ?.getLong(EXTRA_DURATION_MS)
            ?.takeIf { it > 0L }
        return PlayableTrack(
            id = trackId,
            title = metadata.title?.toString().orEmpty(),
            subtitle = extras?.getString(EXTRA_SUBTITLE) ?: metadata.artist?.toString(),
            artworkUri = artworkUri,
            durationMs = durationMs,
        )
    }

    private fun TrackQuality.toQueryValue(): String {
        return when (this) {
            TrackQuality.FAST -> "fast"
            TrackQuality.STANDARD -> "standard"
            TrackQuality.HIGH -> "high"
            TrackQuality.LOSSLESS -> "lossless"
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }
    }

    private suspend fun onControllerThread(block: suspend () -> Unit) {
        withContext(Dispatchers.Main.immediate) {
            block()
        }
    }

    private companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val EXTRA_TRACK_ID = "playable_track_id"
        private const val EXTRA_SUBTITLE = "playable_track_subtitle"
        private const val EXTRA_ARTWORK_URI = "playable_track_artwork_uri"
        private const val EXTRA_DURATION_MS = "playable_track_duration_ms"
        private val QUALITY_PREFERENCE = listOf(
            TrackQuality.STANDARD,
            TrackQuality.HIGH,
            TrackQuality.FAST,
            TrackQuality.LOSSLESS,
        )
    }

    private data class PlaybackDataSourceDescriptor(
        val uri: Uri,
        val isLocalFile: Boolean = false,
    )
}

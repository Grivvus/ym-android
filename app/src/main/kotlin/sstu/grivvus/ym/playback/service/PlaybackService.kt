package sstu.grivvus.ym.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import sstu.grivvus.ym.AppIntents
import sstu.grivvus.ym.MainActivity
import sstu.grivvus.ym.di.PlaybackHttpClient
import sstu.grivvus.ym.playback.artwork.CachedPlaybackArtworkBitmapLoader
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playbackSessionCallback: PlaybackSessionCallback

    @Inject
    @PlaybackHttpClient
    lateinit var playbackHttpClient: OkHttpClient

    @Inject
    lateinit var artworkBitmapLoader: CachedPlaybackArtworkBitmapLoader

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(playbackHttpClient),
        )
        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                        Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING,
            )
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAmrExtractorFlags(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory),
            )
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
            }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createOpenPlayerPendingIntent())
            .setCallback(playbackSessionCallback)
            .setBitmapLoader(artworkBitmapLoader)
            .build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession {
        return checkNotNull(mediaSession) {
            "MediaSession is not initialized"
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun createOpenPlayerPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(AppIntents.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            OPEN_PLAYER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        private const val OPEN_PLAYER_REQUEST_CODE = 1001
    }
}

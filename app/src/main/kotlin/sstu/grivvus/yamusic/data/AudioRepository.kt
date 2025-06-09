package sstu.grivvus.yamusic.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import sstu.grivvus.yamusic.data.local.AudioTrack
import sstu.grivvus.yamusic.di.DefaultDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.core.net.toUri
import sstu.grivvus.yamusic.data.network.AudioNetClient
import sstu.grivvus.yamusic.data.network.netTrackToLocal
import java.io.File
import java.io.IOException

class AudioRepository @Inject constructor(
    val dao: AudioTrackDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context
) {
    private val contentResolver = context.contentResolver
    private val audioClient = AudioNetClient()

    init {
        Log.i("AudioRepository", "audio repository INIT")
    }

    suspend fun getAllInitialTracks(): List<AudioTrack> {
        Log.i("AudioRepository", "getAllInitialTracks start")
        val tracks = audioClient.fetchTracks()
        var ret: List<AudioTrack> = listOf()
        for (track in tracks) {
            val localOne = netTrackToLocal(track)
            dao.insert(localOne)
            ret = ret + localOne
        }
        Log.i("AudioRepository", "getAllInitialTracks finish")
        return ret
    }

    suspend fun uploadTrack(
        file: File, name: String, artist: String?, album: String?,
        callback: (Result<String>) -> Unit

    ): Unit {
        val servId = audioClient.uploadTrack(file, name, artist, album, callback)
        if (servId == null) {
             throw IllegalArgumentException("servId return as null or unkonvertable string")
        }
        dao.insert(AudioTrack(
            servId = servId, title = name,
            artist = artist ?: "unknown",
            album = album ?: "unknown",
            uri = "".toUri()
        ))
    }

    suspend fun downloadTrack(track: AudioTrack): AudioTrack {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "tracks")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "${track.title.replace(Regex("[^a-zA-Z0-9]"), "_")}.mp3"
            val outputFile = File(dir, fileName)

            val data = audioClient.downloadTrack(track)

            data?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            track.copy(
                localPath = outputFile.absolutePath,
                isDownloaded = true
            )
        }
    }
}
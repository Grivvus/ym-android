package sstu.grivvus.ym.playback.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
@UnstableApi
class CachedPlaybackArtworkBitmapLoader @Inject constructor(
    private val artworkCache: PlaybackArtworkCache,
) : BitmapLoader {
    private val executorService = MoreExecutors.listeningDecorator(
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "playback-artwork-loader")
        },
    )

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executorService.submit<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IOException("Unable to decode artwork data")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executorService.submit<Bitmap> {
            val localArtworkUri = runBlocking {
                artworkCache.ensureLocalArtwork(uri)
            } ?: throw IOException("Unable to cache artwork")
            decodeLocalArtwork(localArtworkUri)
        }
    }

    private fun decodeLocalArtwork(uri: Uri): Bitmap {
        val path = uri.path?.takeIf { it.isNotBlank() }
            ?: throw IOException("Cached artwork file path is empty")
        return BitmapFactory.decodeFile(path)
            ?: throw IOException("Unable to decode cached artwork")
    }
}

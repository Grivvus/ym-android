package sstu.grivvus.ym.playback.artwork

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import sstu.grivvus.ym.di.IoDispatcher
import sstu.grivvus.ym.di.PlaybackHttpClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface PlaybackArtworkCache {
    suspend fun ensureLocalArtwork(sourceUri: Uri?): Uri?

    fun cachedLocalArtworkUri(sourceUri: Uri?): Uri?

    suspend fun clear()
}

@Singleton
class FilePlaybackArtworkCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PlaybackHttpClient private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaybackArtworkCache {
    private val cacheMutex = Mutex()

    override suspend fun ensureLocalArtwork(sourceUri: Uri?): Uri? = withContext(ioDispatcher) {
        val uri = sourceUri ?: return@withContext null
        val scheme = uri.scheme?.lowercase()
        if (scheme == FILE_SCHEME) {
            return@withContext uri.takeIf { uri.toFileOrNull()?.exists() == true }
        }

        cacheMutex.withLock {
            val targetFile = cacheFileFor(uri)
            if (targetFile.isValidArtworkFile()) {
                targetFile.setLastModified(System.currentTimeMillis())
                return@withLock Uri.fromFile(targetFile)
            }

            runCatching {
                cacheDirectory.mkdirs()
                val tempFile =
                    File.createTempFile("${targetFile.name}.", TEMP_EXTENSION, cacheDirectory)
                try {
                    writeSourceToFile(uri, tempFile)
                    if (!tempFile.isValidArtworkFile()) {
                        throw IOException("Downloaded artwork is empty")
                    }
                    moveFile(tempFile, targetFile)
                    trimCacheLocked()
                    Uri.fromFile(targetFile)
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }.getOrNull()
        }
    }

    override fun cachedLocalArtworkUri(sourceUri: Uri?): Uri? {
        val uri = sourceUri ?: return null
        if (uri.scheme?.lowercase() == FILE_SCHEME) {
            return uri.takeIf { uri.toFileOrNull()?.exists() == true }
        }
        val cachedFile = cacheFileFor(uri)
        return Uri.fromFile(cachedFile).takeIf { cachedFile.isValidArtworkFile() }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            cacheMutex.withLock {
                cacheDirectory.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun writeSourceToFile(uri: Uri, targetFile: File) {
        when (uri.scheme?.lowercase()) {
            HTTP_SCHEME, HTTPS_SCHEME -> downloadArtwork(uri, targetFile)
            CONTENT_SCHEME -> copyContentArtwork(uri, targetFile)
            else -> throw IOException("Unsupported artwork uri scheme: ${uri.scheme}")
        }
    }

    private fun downloadArtwork(uri: Uri, targetFile: File) {
        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Artwork request failed with status ${response.code}")
            }
            val body = response.body
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun copyContentArtwork(uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                throw IOException("Unable to open artwork content uri")
            }
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun moveFile(sourceFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Unable to replace cached artwork")
        }
        if (!sourceFile.renameTo(targetFile)) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }
        targetFile.setLastModified(System.currentTimeMillis())
    }

    private fun trimCacheLocked() {
        val cachedFiles = cacheDirectory.listFiles()
            ?.filter { file -> file.isFile && file.name.endsWith(CACHE_EXTENSION) }
            ?.sortedByDescending(File::lastModified)
            ?: return

        var totalBytes = 0L
        cachedFiles.forEachIndexed { index, file ->
            totalBytes += file.length()
            if (index >= MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES) {
                file.delete()
            }
        }
    }

    private fun cacheFileFor(uri: Uri): File {
        return File(cacheDirectory, "${sha256(uri.toString())}$CACHE_EXTENSION")
    }

    private val cacheDirectory: File
        get() = File(context.filesDir, CACHE_DIRECTORY_NAME)

    private fun File.isValidArtworkFile(): Boolean = isFile && length() > 0L

    private fun Uri.toFileOrNull(): File? {
        return path?.takeIf { it.isNotBlank() }?.let(::File)
    }

    private companion object {
        private const val CACHE_DIRECTORY_NAME = "media_session_artwork"
        private const val CACHE_EXTENSION = ".artwork"
        private const val TEMP_EXTENSION = ".tmp"
        private const val FILE_SCHEME = "file"
        private const val CONTENT_SCHEME = "content"
        private const val HTTP_SCHEME = "http"
        private const val HTTPS_SCHEME = "https"
        private const val MAX_CACHE_FILES = 100
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

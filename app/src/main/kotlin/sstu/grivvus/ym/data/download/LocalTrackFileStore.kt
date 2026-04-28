package sstu.grivvus.ym.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.di.IoDispatcher

@Singleton
class LocalTrackFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun findDownloadedFile(trackId: Long): File? = withContext(ioDispatcher) {
        downloadDirectory.listFiles()
            ?.firstOrNull { file -> file.isFinalTrackFile(trackId) }
    }

    suspend fun findDownloadedFiles(trackIds: Collection<Long>): Map<Long, File> =
        withContext(ioDispatcher) {
            val distinctTrackIds = trackIds.distinct()
            if (distinctTrackIds.isEmpty()) {
                return@withContext emptyMap()
            }
            val files = downloadDirectory.listFiles()
                ?.filter(File::isFile)
                .orEmpty()
            distinctTrackIds.mapNotNull { trackId ->
                files.firstOrNull { file -> file.isFinalTrackFile(trackId) }
                    ?.let { file -> trackId to file }
            }.toMap()
        }

    suspend fun prepareTempFile(trackId: Long): File = withContext(ioDispatcher) {
        downloadDirectory.mkdirs()
        tempFile(trackId).also { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to replace partial track download")
            }
        }
    }

    suspend fun finalFileFor(trackId: Long, contentType: String?): File = withContext(ioDispatcher) {
        downloadDirectory.mkdirs()
        File(downloadDirectory, "$TRACK_FILE_PREFIX$trackId-$DOWNLOAD_QUALITY.${extensionFor(contentType)}")
    }

    suspend fun promoteTempFile(trackId: Long, tempFile: File, finalFile: File): File =
        withContext(ioDispatcher) {
            downloadDirectory.mkdirs()
            deleteTrackFilesLocked(trackId, except = tempFile)
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                if (!tempFile.delete()) {
                    throw IOException("Unable to remove partial track download")
                }
            }
            finalFile
        }

    suspend fun deleteTrackFiles(trackId: Long) {
        withContext(ioDispatcher) {
            deleteTrackFilesLocked(trackId)
        }
    }

    suspend fun deleteTrackFiles(trackIds: Collection<Long>) {
        withContext(ioDispatcher) {
            trackIds.forEach(::deleteTrackFilesLocked)
        }
    }

    suspend fun clearAll() {
        withContext(ioDispatcher) {
            downloadDirectory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        }
    }

    private fun tempFile(trackId: Long): File {
        return File(downloadDirectory, "$TRACK_FILE_PREFIX$trackId-$DOWNLOAD_QUALITY$PART_EXTENSION")
    }

    private fun deleteTrackFilesLocked(trackId: Long, except: File? = null) {
        downloadDirectory.listFiles()
            ?.filter { file -> file.isTrackFile(trackId) }
            ?.filterNot { file -> except != null && file.absolutePath == except.absolutePath }
            ?.forEach { file -> file.delete() }
    }

    private fun File.isFinalTrackFile(trackId: Long): Boolean {
        return isFile &&
                length() > 0L &&
                isTrackFile(trackId) &&
                !name.endsWith(PART_EXTENSION) &&
                !name.endsWith(METADATA_EXTENSION)
    }

    private fun File.isTrackFile(trackId: Long): Boolean {
        return name.startsWith("$TRACK_FILE_PREFIX$trackId-")
    }

    private val downloadDirectory: File
        get() = File(context.noBackupFilesDir, DOWNLOAD_DIRECTORY_NAME)

    private companion object {
        private const val DOWNLOAD_DIRECTORY_NAME = "downloaded_tracks"
        private const val TRACK_FILE_PREFIX = "track-"
        private const val DOWNLOAD_QUALITY = "standard"
        private const val PART_EXTENSION = ".part"
        private const val METADATA_EXTENSION = ".json"

        private fun extensionFor(contentType: String?): String {
            return when (contentType?.substringBefore(";")?.trim()?.lowercase()) {
                "audio/aac" -> "aac"
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/m4a", "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/ogg", "application/ogg" -> "ogg"
                "audio/wav", "audio/wave", "audio/x-wav" -> "wav"
                else -> "audio"
            }
        }
    }
}

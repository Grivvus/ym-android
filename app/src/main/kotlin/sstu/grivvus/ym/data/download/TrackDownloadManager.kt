package sstu.grivvus.ym.data.download

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.local.AudioTrackDao
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.auth.SessionState
import sstu.grivvus.ym.data.network.remote.track.TrackDownloadMetadata
import sstu.grivvus.ym.data.network.remote.track.TrackDownloadRemoteDataSource
import sstu.grivvus.ym.di.ApplicationScope
import sstu.grivvus.ym.di.IoDispatcher

sealed interface TrackDownloadEvent {
    data class Downloaded(val trackId: Long) : TrackDownloadEvent
    data class LocalCopyDeleted(val trackId: Long) : TrackDownloadEvent
    data class Failed(val trackId: Long, val operation: TrackDownloadOperation, val error: Throwable) :
        TrackDownloadEvent
}

enum class TrackDownloadOperation {
    DOWNLOAD,
    DELETE_LOCAL_COPY,
}

@Singleton
class TrackDownloadManager @Inject constructor(
    private val remoteDataSource: TrackDownloadRemoteDataSource,
    private val localTrackFileStore: LocalTrackFileStore,
    private val audioTrackDao: AudioTrackDao,
    private val authSessionManager: AuthSessionManager,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val runningLock = Any()
    private val runningJobs = mutableMapOf<Long, Job>()
    private val _downloadingTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _events = MutableSharedFlow<TrackDownloadEvent>(extraBufferCapacity = 8)

    val downloadingTrackIds: StateFlow<Set<Long>> = _downloadingTrackIds.asStateFlow()
    val events: SharedFlow<TrackDownloadEvent> = _events.asSharedFlow()

    init {
        observeSessionEnd()
    }

    fun downloadTrack(trackId: Long) {
        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                downloadTrackInternal(trackId)
                _events.emit(TrackDownloadEvent.Downloaded(trackId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.emit(
                    TrackDownloadEvent.Failed(
                        trackId = trackId,
                        operation = TrackDownloadOperation.DOWNLOAD,
                        error = error,
                    ),
                )
            } finally {
                markDownloadFinished(trackId)
            }
        }
        if (!markDownloadStarted(trackId, job)) {
            return
        }
        job.start()
    }

    fun deleteLocalCopy(trackId: Long) {
        if (trackId in _downloadingTrackIds.value) {
            return
        }
        applicationScope.launch {
            try {
                localTrackFileStore.deleteTrackFiles(trackId)
                clearLocalTrackPath(trackId)
                _events.emit(TrackDownloadEvent.LocalCopyDeleted(trackId))
            } catch (error: Exception) {
                _events.emit(
                    TrackDownloadEvent.Failed(
                        trackId = trackId,
                        operation = TrackDownloadOperation.DELETE_LOCAL_COPY,
                        error = error,
                    ),
                )
            }
        }
    }

    fun cancelDownloads(trackIds: Collection<Long>) {
        val requestedTrackIds = trackIds.toSet()
        val jobs = synchronized(runningLock) {
            val jobsToCancel = runningJobs
                .filterKeys { trackId -> trackId in requestedTrackIds }
                .values
                .toList()
            runningJobs.keys.removeAll(requestedTrackIds)
            _downloadingTrackIds.value = _downloadingTrackIds.value - requestedTrackIds
            jobsToCancel
        }
        jobs.forEach { job -> job.cancel() }
    }

    private suspend fun downloadTrackInternal(trackId: Long) {
        val metadata = remoteDataSource.headTrack(trackId)
        val tempFile = localTrackFileStore.prepareTempFile(trackId)
        try {
            remoteDataSource.downloadTrack(trackId, tempFile)
            currentCoroutineContext().ensureActive()
            validateDownload(tempFile, metadata)
            val finalFile = localTrackFileStore.finalFileFor(trackId, metadata.contentType)
            val downloadedFile = localTrackFileStore.promoteTempFile(trackId, tempFile, finalFile)
            markTrackDownloaded(trackId, downloadedFile)
        } catch (error: CancellationException) {
            deleteTempFile(tempFile)
            throw error
        } catch (error: Exception) {
            deleteTempFile(tempFile)
            throw error
        }
    }

    private suspend fun deleteTempFile(tempFile: File) {
        withContext(ioDispatcher + NonCancellable) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun markTrackDownloaded(trackId: Long, file: File) {
        withContext(ioDispatcher) {
            audioTrackDao.getById(trackId)?.let { track ->
                audioTrackDao.upsert(
                    track.copy(
                        localPath = file.absolutePath,
                        isDownloaded = true,
                    ),
                )
            }
        }
    }

    private suspend fun clearLocalTrackPath(trackId: Long) {
        withContext(ioDispatcher) {
            audioTrackDao.getById(trackId)?.let { track ->
                audioTrackDao.upsert(
                    track.copy(
                        localPath = null,
                        isDownloaded = false,
                    ),
                )
            }
        }
    }

    private suspend fun validateDownload(file: File, metadata: TrackDownloadMetadata) {
        withContext(ioDispatcher) {
            metadata.contentLength
                ?.takeIf { expectedLength -> expectedLength >= 0L }
                ?.let { expectedLength ->
                    if (file.length() != expectedLength) {
                        throw IOException("Downloaded track size does not match server metadata")
                    }
                }

            val expectedChecksum = metadata.checksumSha256.normalizedSha256OrNull()
            if (expectedChecksum != null && sha256(file) != expectedChecksum) {
                throw IOException("Downloaded track checksum does not match server metadata")
            }
        }
    }

    private fun observeSessionEnd() {
        applicationScope.launch {
            authSessionManager.sessionState.collectLatest { state ->
                if (state is SessionState.Unauthenticated) {
                    cancelAllDownloads()
                }
            }
        }
    }

    private fun markDownloadStarted(trackId: Long, job: Job): Boolean {
        return synchronized(runningLock) {
            if (trackId in runningJobs) {
                false
            } else {
                runningJobs[trackId] = job
                _downloadingTrackIds.update { it + trackId }
                true
            }
        }
    }

    private fun markDownloadFinished(trackId: Long) {
        synchronized(runningLock) {
            runningJobs.remove(trackId)
            _downloadingTrackIds.update { it - trackId }
        }
    }

    private fun cancelAllDownloads() {
        val jobs = synchronized(runningLock) {
            val currentJobs = runningJobs.values.toList()
            runningJobs.clear()
            _downloadingTrackIds.value = emptySet()
            currentJobs
        }
        jobs.forEach { job -> job.cancel() }
    }

    private fun String?.normalizedSha256OrNull(): String? {
        return this
            ?.trim()
            ?.trim('"')
            ?.removePrefix("sha256:")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

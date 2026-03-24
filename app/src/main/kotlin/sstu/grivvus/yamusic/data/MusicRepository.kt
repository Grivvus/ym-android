package sstu.grivvus.yamusic.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.yamusic.data.local.LibraryTrack
import sstu.grivvus.yamusic.data.local.LibraryTrackDao
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.Playlist
import sstu.grivvus.yamusic.data.local.PlaylistDao
import sstu.grivvus.yamusic.data.local.PlaylistTrackCrossRef
import sstu.grivvus.yamusic.data.local.PlaylistTrackDao
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.data.network.OpenApiNetworkClient
import sstu.grivvus.yamusic.di.DefaultDispatcher
import sstu.grivvus.yamusic.playlistCoverUri
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class PlaylistBundle(
    val playlist: Playlist,
    val tracks: List<LibraryTrack>,
)

data class MusicLibraryData(
    val playlists: List<PlaylistBundle>,
    val libraryTracks: List<LibraryTrack>,
)

class MusicRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val libraryTrackDao: LibraryTrackDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val userDao: UserDao,
    private val networkClient: OpenApiNetworkClient,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun loadLibrary(refreshFromNetwork: Boolean): MusicLibraryData =
        withContext(dispatcher) {
            if (refreshFromNetwork) {
                syncRemoteState()
            }
            buildLocalState()
        }

    suspend fun createPlaylist(name: String, coverUri: Uri?): MusicLibraryData =
        withContext(dispatcher) {
            val user = requireActiveUser()
            val preparedCover = coverUri?.let(::prepareUploadFile)
            try {
                val playlistId = networkClient.createPlaylist(
                    userId = user.remoteId,
                    accessToken = user.access,
                    playlistName = name,
                    coverFile = preparedCover?.file,
                    coverMimeType = preparedCover?.mimeType,
                )
                playlistDao.upsert(
                    Playlist(
                        remoteId = playlistId,
                        name = name,
                        coverUri = preparedCover?.let {
                            playlistCoverUri(
                                playlistId,
                                cacheBust = true
                            )
                        },
                        nameIsLocalOverride = false,
                        tracksSeeded = true,
                    )
                )
                buildLocalState()
            } finally {
                preparedCover?.file?.delete()
            }
        }

    suspend fun deletePlaylist(playlistId: Long): MusicLibraryData = withContext(dispatcher) {
        val user = requireActiveUser()
        networkClient.deletePlaylist(
            userId = user.remoteId,
            accessToken = user.access,
            playlistId = playlistId,
        )
        playlistDao.deleteById(playlistId)
        buildLocalState()
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String): MusicLibraryData =
        withContext(dispatcher) {
            val currentPlaylist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            val user = requireActiveUser()
            val updatedPlaylist = networkClient.updatePlaylist(
                userId = user.remoteId,
                accessToken = user.access,
                playlistId = playlistId,
                playlistName = newName,
            )
            playlistTrackDao.deleteForPlaylist(playlistId)
            playlistTrackDao.insertAll(
                updatedPlaylist.tracks.map { trackId ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId.toLong(),
                    )
                }
            )
            playlistDao.upsert(
                currentPlaylist.copy(
                    name = updatedPlaylist.playlistName,
                    coverUri = updatedPlaylist.coverUrl?.toUri() ?: currentPlaylist.coverUri,
                    nameIsLocalOverride = false,
                    tracksSeeded = true,
                )
            )
            buildLocalState()
        }

    suspend fun uploadPlaylistCover(playlistId: Long, coverUri: Uri): MusicLibraryData =
        withContext(dispatcher) {
            val user = requireActiveUser()
            val preparedCover = prepareUploadFile(coverUri)
            try {
                networkClient.uploadPlaylistCover(
                    userId = user.remoteId,
                    accessToken = user.access,
                    playlistId = playlistId,
                    coverFile = preparedCover.file,
                    coverMimeType = preparedCover.mimeType,
                )
            } finally {
                preparedCover.file.delete()
            }

            val playlist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            playlistDao.upsert(
                playlist.copy(
                    coverUri = playlistCoverUri(playlistId, cacheBust = true),
                )
            )
            buildLocalState()
        }

    suspend fun addTracksToPlaylist(
        playlistId: Long,
        trackIds: Collection<Long>,
    ): MusicLibraryData = withContext(dispatcher) {
        val user = requireActiveUser()
        val playlist = playlistDao.getById(playlistId)
            ?: throw IOException("Playlist was not found")
        trackIds.forEach { trackId ->
            networkClient.addTrackToPlaylist(
                userId = user.remoteId,
                accessToken = user.access,
                playlistId = playlistId,
                trackId = trackId,
            )
        }
        playlistTrackDao.insertAll(
            trackIds.map { trackId ->
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId)
            }
        )
        if (!playlist.tracksSeeded) {
            playlistDao.upsert(playlist.copy(tracksSeeded = true))
        }
        buildLocalState()
    }

    suspend fun uploadTrackAndAddToPlaylist(
        playlistId: Long,
        trackUri: Uri,
        title: String,
        artistId: Long,
        albumId: Long,
    ): MusicLibraryData = withContext(dispatcher) {
        val user = requireActiveUser()
        val preparedTrack = prepareUploadFile(trackUri)
        try {
            val trackId = networkClient.uploadTrack(
                userId = user.remoteId,
                accessToken = user.access,
                name = title,
                artistId = artistId,
                albumId = albumId,
                trackFile = preparedTrack.file,
                trackMimeType = preparedTrack.mimeType,
            )
            libraryTrackDao.upsert(
                LibraryTrack(
                    remoteId = trackId,
                    name = title,
                    artistId = artistId,
                )
            )
            val playlist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            networkClient.addTrackToPlaylist(
                userId = user.remoteId,
                accessToken = user.access,
                playlistId = playlistId,
                trackId = trackId,
            )
            playlistTrackDao.insert(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId
                )
            )
            if (!playlist.tracksSeeded) {
                playlistDao.upsert(playlist.copy(tracksSeeded = true))
            }
            buildLocalState()
        } finally {
            preparedTrack.file.delete()
        }
    }

    private suspend fun syncRemoteState() {
        val user = requireActiveUser()
        val remoteTracks = networkClient.getTracks(
            userId = user.remoteId,
            accessToken = user.access,
        )
        libraryTrackDao.upsertAll(
            remoteTracks.map { track ->
                LibraryTrack(
                    remoteId = track.trackId.toLong(),
                    name = track.name,
                    artistId = track.artistId.toLong(),
                    coverUri = track.coverUrl?.toUri(),
                )
            }
        )

        val remotePlaylistSummaries = networkClient.getPlaylists(
            userId = user.remoteId,
            accessToken = user.access,
        )
        val remotePlaylists = remotePlaylistSummaries.map { summary ->
            networkClient.getPlaylist(
                userId = user.remoteId,
                accessToken = user.access,
                playlistId = summary.playlistId.toLong(),
            )
        }
        val existingPlaylists = playlistDao.getAll().associateBy { it.remoteId }
        val remoteIds = remotePlaylistSummaries.map { it.playlistId.toLong() }.toSet()

        existingPlaylists.values
            .filterNot { it.remoteId in remoteIds }
            .forEach { stalePlaylist ->
                playlistDao.deleteById(stalePlaylist.remoteId)
            }

        playlistDao.upsertAll(
            remotePlaylistSummaries.map { remotePlaylist ->
                val existingPlaylist = existingPlaylists[remotePlaylist.playlistId.toLong()]
                Playlist(
                    remoteId = remotePlaylist.playlistId.toLong(),
                    name = remotePlaylist.playlistName,
                    coverUri = existingPlaylist?.coverUri,
                    nameIsLocalOverride = false,
                    tracksSeeded = existingPlaylist?.tracksSeeded ?: false,
                )
            }
        )

        remotePlaylists.forEach { remotePlaylist ->
            val playlistId = remotePlaylist.playlistId.toLong()
            val localPlaylist = playlistDao.getById(playlistId) ?: return@forEach
            playlistTrackDao.deleteForPlaylist(playlistId)
            playlistTrackDao.insertAll(
                remotePlaylist.tracks.map { trackId ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId.toLong(),
                    )
                }
            )
            playlistDao.upsert(
                localPlaylist.copy(
                    coverUri = remotePlaylist.coverUrl?.toUri() ?: localPlaylist.coverUri,
                    tracksSeeded = true,
                )
            )
        }
    }

    private suspend fun buildLocalState(): MusicLibraryData {
        val libraryTracks = libraryTrackDao.getAll()
        val trackMap = libraryTracks.associateBy { it.remoteId }
        val playlists = playlistDao.getAll().map { playlist ->
            val tracks =
                playlistTrackDao.getTrackIdsForPlaylist(playlist.remoteId).mapNotNull { trackId ->
                    trackMap[trackId]
                }
            PlaylistBundle(playlist = playlist, tracks = tracks)
        }
        return MusicLibraryData(
            playlists = playlists,
            libraryTracks = libraryTracks,
        )
    }

    private suspend fun requireActiveUser(): LocalUser {
        return userDao.getActiveUser() ?: throw IOException("Active user was not found")
    }

    private fun prepareUploadFile(sourceUri: Uri): PreparedUploadFile {
        val mimeType = context.contentResolver.getType(sourceUri)
        val extension = when {
            mimeType == null -> ".tmp"
            mimeType.endsWith("jpeg") || mimeType.endsWith("jpg") -> ".jpg"
            mimeType.endsWith("png") -> ".png"
            mimeType.endsWith("webp") -> ".webp"
            mimeType.startsWith("audio/") -> ".audio"
            else -> ".tmp"
        }
        val tempFile = File.createTempFile("music_upload_", extension, context.cacheDir)
        context.contentResolver.openInputStream(sourceUri).use { input ->
            if (input == null) {
                throw IOException("Unable to read the selected file")
            }
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return PreparedUploadFile(file = tempFile, mimeType = mimeType)
    }

    private data class PreparedUploadFile(
        val file: File,
        val mimeType: String?,
    )
}

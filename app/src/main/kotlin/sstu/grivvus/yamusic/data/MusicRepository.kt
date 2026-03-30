package sstu.grivvus.yamusic.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.yamusic.data.local.Album
import sstu.grivvus.yamusic.data.local.AlbumDao
import sstu.grivvus.yamusic.data.local.Artist
import sstu.grivvus.yamusic.data.local.ArtistDao
import sstu.grivvus.yamusic.data.local.AudioTrack
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.Playlist
import sstu.grivvus.yamusic.data.local.PlaylistDao
import sstu.grivvus.yamusic.data.local.PlaylistTrackCrossRef
import sstu.grivvus.yamusic.data.local.PlaylistTrackDao
import sstu.grivvus.yamusic.data.local.TrackAlbumCrossRef
import sstu.grivvus.yamusic.data.local.TrackAlbumDao
import sstu.grivvus.yamusic.data.network.AuthSessionManager
import sstu.grivvus.yamusic.data.network.OpenApiNetworkClient
import sstu.grivvus.yamusic.di.DefaultDispatcher
import sstu.grivvus.yamusic.openapi.models.TrackMetadata
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class TrackBundle(
    val track: AudioTrack,
    val albums: List<Album>,
)

data class PlaylistBundle(
    val playlist: Playlist,
    val tracks: List<TrackBundle>,
)

data class MusicLibraryData(
    val playlists: List<PlaylistBundle>,
    val libraryTracks: List<TrackBundle>,
)

class MusicRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val audioTrackDao: AudioTrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val trackAlbumDao: TrackAlbumDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val networkClient: OpenApiNetworkClient,
    private val authSessionManager: AuthSessionManager,
    private val serverInfoRepository: ServerInfoRepository,
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
                            serverInfoRepository.playlistCoverUri(
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
                    coverUri = serverInfoRepository.playlistCoverUri(playlistId, cacheBust = true),
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
            artistDao.upsert(
                Artist(
                    remoteId = artistId,
                    name = "",
                )
            )
            val existingAlbum = albumDao.getById(albumId)
            albumDao.upsert(
                Album(
                    remoteId = albumId,
                    artistId = artistId,
                    name = existingAlbum?.name.orEmpty(),
                    coverUri = existingAlbum?.coverUri,
                )
            )
            audioTrackDao.upsert(
                AudioTrack(
                    remoteId = trackId,
                    name = title,
                    artistId = artistId,
                )
            )
            trackAlbumDao.insert(TrackAlbumCrossRef(trackId = trackId, albumId = albumId))
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
        val existingTracks = audioTrackDao.getAll().associateBy { it.remoteId }
        val existingAlbums = albumDao.getAll().associateBy { it.remoteId }

        artistDao.upsertAll(
            remoteTracks
                .map { track ->
                    Artist(
                        remoteId = track.artistId.toLong(),
                        name = "",
                    )
                }
                .distinctBy { it.remoteId }
        )

        albumDao.upsertAll(
            remoteTracks
                .map { track ->
                    track.toAlbum(existingAlbums = existingAlbums)
                }
                .distinctBy { it.remoteId }
        )

        audioTrackDao.upsertAll(
            remoteTracks.map { track ->
                val existingTrack = existingTracks[track.trackId.toLong()]
                AudioTrack(
                    remoteId = track.trackId.toLong(),
                    name = track.name,
                    artistId = track.artistId.toLong(),
                    durationMs = existingTrack?.durationMs,
                    uriFast = track.trackFastPreset?.toUri() ?: existingTrack?.uriFast,
                    uriStandard = track.trackStandardPreset?.toUri() ?: existingTrack?.uriStandard,
                    uriHigh = track.trackHighPreset?.toUri() ?: existingTrack?.uriHigh,
                    uriLossless = track.trackLosslessPreset?.toUri() ?: existingTrack?.uriLossless,
                    localPath = existingTrack?.localPath,
                    isDownloaded = existingTrack?.isDownloaded ?: false,
                )
            }
        )

        trackAlbumDao.clearAll()
        trackAlbumDao.insertAll(
            remoteTracks.map { track ->
                TrackAlbumCrossRef(
                    trackId = track.trackId.toLong(),
                    albumId = track.albumId.toLong(),
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
        val tracks = audioTrackDao.getAll()
        val albumsById = albumDao.getAll().associateBy { it.remoteId }
        val albumsByTrackId = trackAlbumDao.getAll()
            .groupBy { it.trackId }
            .mapValues { (_, refs) ->
                refs.mapNotNull { ref -> albumsById[ref.albumId] }
                    .sortedBy { album -> album.remoteId }
            }
        val libraryTracks = tracks.map { track ->
            TrackBundle(
                track = track,
                albums = albumsByTrackId[track.remoteId].orEmpty(),
            )
        }
        val trackMap = libraryTracks.associateBy { it.track.remoteId }
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
        return authSessionManager.requireActiveUser()
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

    private fun TrackMetadata.toAlbum(
        existingAlbums: Map<Long, Album>,
    ): Album {
        val existingAlbum = existingAlbums[albumId.toLong()]
        return Album(
            remoteId = albumId.toLong(),
            artistId = artistId.toLong(),
            name = existingAlbum?.name.orEmpty(),
            coverUri = coverUrl?.toUri() ?: existingAlbum?.coverUri,
        )
    }
}

package sstu.grivvus.ym.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.AlbumDao
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.local.ArtistDao
import sstu.grivvus.ym.data.local.AudioTrack
import sstu.grivvus.ym.data.local.AudioTrackDao
import sstu.grivvus.ym.data.local.Playlist
import sstu.grivvus.ym.data.local.PlaylistDao
import sstu.grivvus.ym.data.local.PlaylistTrackCrossRef
import sstu.grivvus.ym.data.local.PlaylistTrackDao
import sstu.grivvus.ym.data.local.TrackAlbumCrossRef
import sstu.grivvus.ym.data.local.TrackAlbumDao
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.core.ConflictApiException
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.data.network.model.UploadPart
import sstu.grivvus.ym.data.network.remote.album.AlbumRemoteDataSource
import sstu.grivvus.ym.data.network.remote.artist.ArtistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.ym.di.DefaultDispatcher
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
    val artists: List<Artist>,
    val albums: List<Album>,
)

data class UploadTrackCatalog(
    val artists: List<Artist>,
    val albums: List<Album>,
)

class PlaylistCreationConflict(
    val msg: String = "playlist with this name already exists",
) : Exception(msg)

class MusicRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val audioTrackDao: AudioTrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val trackAlbumDao: TrackAlbumDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val playlistRemoteDataSource: PlaylistRemoteDataSource,
    private val trackRemoteDataSource: TrackRemoteDataSource,
    private val artistRemoteDataSource: ArtistRemoteDataSource,
    private val albumRemoteDataSource: AlbumRemoteDataSource,
    private val serverInfoRepository: ServerInfoRepository,
    private val authSessionManager: AuthSessionManager,
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

    suspend fun loadUploadTrackCatalog(refreshFromNetwork: Boolean): UploadTrackCatalog =
        withContext(dispatcher) {
            if (refreshFromNetwork) {
                syncRemoteState()
            }
            UploadTrackCatalog(
                artists = artistDao.getAll(),
                albums = albumDao.getAll(),
            )
        }

    suspend fun createPlaylist(name: String, coverUri: Uri?): MusicLibraryData =
        withContext(dispatcher) {
            val preparedCover = coverUri?.let(::prepareUploadFile)
            val playlist =
                playlistDao.getByUserAndName(authSessionManager.requireCurrentUser().remoteId, name)
            if (playlist != null) {
                throw PlaylistCreationConflict("Playlist with this name already exists")
            }
            try {
                val playlistId = playlistRemoteDataSource.createPlaylist(
                    name = name,
                    cover = preparedCover?.toUploadPart(),
                )
                playlistDao.upsert(
                    Playlist(
                        remoteId = playlistId,
                        name = name,
                        coverUri = preparedCover?.let {
                            serverInfoRepository.playlistCoverUri(playlistId, cacheBust = true)
                        },
                        ownerRemoteId = authSessionManager.requireCurrentUser().remoteId,
                        nameIsLocalOverride = false,
                        tracksSeeded = true,
                    ),
                )
                buildLocalState()
            } catch (e: ConflictApiException) {
                throw PlaylistCreationConflict(e.message)
            } finally {
                preparedCover?.file?.delete()
            }
        }

    suspend fun deletePlaylist(playlistId: Long): MusicLibraryData = withContext(dispatcher) {
        playlistRemoteDataSource.deletePlaylist(playlistId)
        playlistDao.deleteById(playlistId)
        buildLocalState()
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String): MusicLibraryData =
        withContext(dispatcher) {
            val currentPlaylist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            val updatedPlaylist = playlistRemoteDataSource.updatePlaylist(playlistId, newName)
            playlistTrackDao.deleteForPlaylist(playlistId)
            playlistTrackDao.insertAll(
                updatedPlaylist.trackIds.map { trackId ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId,
                    )
                },
            )
            playlistDao.upsert(
                currentPlaylist.copy(
                    name = updatedPlaylist.name,
                    coverUri = updatedPlaylist.coverUrl?.toUri() ?: currentPlaylist.coverUri,
                    nameIsLocalOverride = false,
                    tracksSeeded = true,
                ),
            )
            buildLocalState()
        }

    suspend fun uploadPlaylistCover(playlistId: Long, coverUri: Uri): MusicLibraryData =
        withContext(dispatcher) {
            val preparedCover = prepareUploadFile(coverUri)
            try {
                playlistRemoteDataSource.uploadCover(
                    playlistId = playlistId,
                    cover = preparedCover.toUploadPart(),
                )
            } finally {
                preparedCover.file.delete()
            }

            val playlist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            playlistDao.upsert(
                playlist.copy(
                    coverUri = serverInfoRepository.playlistCoverUri(playlistId, cacheBust = true),
                ),
            )
            buildLocalState()
        }

    suspend fun addTracksToPlaylist(
        playlistId: Long,
        trackIds: Collection<Long>,
    ): MusicLibraryData = withContext(dispatcher) {
        val playlist = playlistDao.getById(playlistId)
            ?: throw IOException("Playlist was not found")
        trackIds.forEach { trackId ->
            playlistRemoteDataSource.addTrack(playlistId, trackId)
        }
        playlistTrackDao.insertAll(
            trackIds.map { trackId ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                )
            },
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
        albumId: Long?,
        isSingle: Boolean,
    ): MusicLibraryData = withContext(dispatcher) {
        val preparedTrack = prepareUploadFile(trackUri)
        try {
            val trackId = trackRemoteDataSource.uploadTrack(
                name = title,
                artistId = artistId,
                albumId = albumId,
                isSingle = isSingle,
                track = preparedTrack.toUploadPart(),
            )
            val existingArtist = artistDao.getById(artistId)
            val remoteArtistMetadata = loadRemoteArtistMetadata(artistId)
            artistDao.upsert(
                Artist(
                    remoteId = artistId,
                    name = remoteArtistMetadata?.name?.takeIf { it.isNotBlank() }
                        ?: existingArtist?.name.orEmpty(),
                    imageUri = remoteArtistMetadata?.imageUri ?: existingArtist?.imageUri,
                ),
            )
            audioTrackDao.upsert(
                AudioTrack(
                    remoteId = trackId,
                    name = title,
                    artistId = artistId,
                ),
            )
            if (!isSingle && albumId != null) {
                val existingAlbum = albumDao.getById(albumId)
                val remoteAlbumMetadata = loadRemoteAlbumMetadata(albumId)
                albumDao.upsert(
                    Album(
                        remoteId = albumId,
                        artistId = artistId,
                        name = remoteAlbumMetadata?.name?.takeIf { it.isNotBlank() }
                            ?: existingAlbum?.name.orEmpty(),
                        coverUri = remoteAlbumMetadata?.coverUri ?: existingAlbum?.coverUri,
                    ),
                )
                trackAlbumDao.upsert(TrackAlbumCrossRef(trackId = trackId, albumId = albumId))
            }
            val playlist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            playlistRemoteDataSource.addTrack(playlistId, trackId)
            playlistTrackDao.upsert(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                ),
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
        val existingArtists = artistRemoteDataSource.getAllArtists()

        artistDao.upsertAll(
            existingArtists.map {
                Artist(it.id, it.name, it.coverUrl?.toUri())
            }
        )

        val remoteTracks = trackRemoteDataSource.getMyTracks()
        val existingTracks = audioTrackDao.getAll().associateBy { it.remoteId }

        audioTrackDao.upsertAll(
            remoteTracks.map { track ->
                val existingTrack = existingTracks[track.id]
                AudioTrack(
                    remoteId = track.id,
                    name = track.name,
                    artistId = track.artistId,
                    durationMs = existingTrack?.durationMs,
                    uriFast = track.qualityPresets[TrackQuality.FAST]?.toUri()
                        ?: existingTrack?.uriFast,
                    uriStandard = track.qualityPresets[TrackQuality.STANDARD]?.toUri()
                        ?: existingTrack?.uriStandard,
                    uriHigh = track.qualityPresets[TrackQuality.HIGH]?.toUri()
                        ?: existingTrack?.uriHigh,
                    uriLossless = track.qualityPresets[TrackQuality.LOSSLESS]?.toUri()
                        ?: existingTrack?.uriLossless,
                    localPath = existingTrack?.localPath,
                    isDownloaded = existingTrack?.isDownloaded ?: false,
                )
            },
        )

        trackAlbumDao.clearAll()
        trackAlbumDao.upsertAll(
            remoteTracks.map { track ->
                TrackAlbumCrossRef(
                    trackId = track.id,
                    albumId = track.albumId,
                )
            },
        )

        val remotePlaylistSummaries = playlistRemoteDataSource.getMyPlaylists()
        val remotePlaylists = remotePlaylistSummaries.map { summary ->
            playlistRemoteDataSource.getPlaylist(summary.id)
        }
        val existingPlaylists = playlistDao.getAll().associateBy { it.remoteId }
        val remoteIds = remotePlaylistSummaries.map { it.id }.toSet()

        existingPlaylists.values
            .filterNot { it.remoteId in remoteIds }
            .forEach { stalePlaylist ->
                playlistDao.deleteById(stalePlaylist.remoteId)
            }

        playlistDao.upsertAll(
            remotePlaylistSummaries.map { remotePlaylist ->
                val existingPlaylist = existingPlaylists[remotePlaylist.id]
                Playlist(
                    remoteId = remotePlaylist.id,
                    name = remotePlaylist.name,
                    coverUri = existingPlaylist?.coverUri,
                    nameIsLocalOverride = false,
                    ownerRemoteId = authSessionManager.requireCurrentUser().remoteId,
                    tracksSeeded = existingPlaylist?.tracksSeeded ?: false,
                )
            },
        )

        remotePlaylists.forEach { remotePlaylist ->
            val playlistId = remotePlaylist.id
            val localPlaylist = playlistDao.getById(playlistId) ?: return@forEach
            playlistTrackDao.deleteForPlaylist(playlistId)
            playlistTrackDao.insertAll(
                remotePlaylist.trackIds.map { trackId ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId,
                    )
                },
            )
            playlistDao.upsert(
                localPlaylist.copy(
                    coverUri = remotePlaylist.coverUrl?.toUri() ?: localPlaylist.coverUri,
                    tracksSeeded = true,
                ),
            )
        }
    }

    private suspend fun buildLocalState(): MusicLibraryData {
        val artists = artistDao.getAll()
        val tracks = audioTrackDao.getAll()
        val albums = albumDao.getAll()
        val albumsById = albums.associateBy { it.remoteId }
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
            val tracksForPlaylist =
                playlistTrackDao.getTrackIdsForPlaylist(playlist.remoteId).mapNotNull { trackId ->
                    trackMap[trackId]
                }
            PlaylistBundle(playlist = playlist, tracks = tracksForPlaylist)
        }
        return MusicLibraryData(
            playlists = playlists,
            libraryTracks = libraryTracks,
            artists = artists,
            albums = albums,
        )
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

    private fun PreparedUploadFile.toUploadPart(): UploadPart {
        return UploadPart(
            file = file,
            mimeType = mimeType,
        )
    }

    private suspend fun loadRemoteArtistMetadata(artistId: Long): RemoteArtistMetadata? {
        return runCatching {
            artistRemoteDataSource.getArtist(artistId)
        }.getOrNull()?.let { artist ->
            RemoteArtistMetadata(
                name = artist.name,
                imageUri = artist.coverUrl?.toUri(),
            )
        }
    }

    private suspend fun loadRemoteAlbumMetadata(albumId: Long): RemoteAlbumMetadata? {
        return runCatching {
            albumRemoteDataSource.getAlbum(albumId)
        }.getOrNull()?.let { album ->
            RemoteAlbumMetadata(
                name = album.name,
                coverUri = album.coverUrl?.toUri(),
            )
        }
    }

    private data class RemoteArtistMetadata(
        val name: String,
        val imageUri: Uri?,
    )

    private data class RemoteAlbumMetadata(
        val name: String,
        val coverUri: Uri?,
    )
}

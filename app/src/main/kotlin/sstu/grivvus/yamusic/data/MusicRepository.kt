package sstu.grivvus.yamusic.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.yamusic.data.local.Album
import sstu.grivvus.yamusic.data.local.AlbumDao
import sstu.grivvus.yamusic.data.local.Artist
import sstu.grivvus.yamusic.data.local.ArtistDao
import sstu.grivvus.yamusic.data.local.AudioTrack
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import sstu.grivvus.yamusic.data.local.Playlist
import sstu.grivvus.yamusic.data.local.PlaylistDao
import sstu.grivvus.yamusic.data.local.PlaylistTrackCrossRef
import sstu.grivvus.yamusic.data.local.PlaylistTrackDao
import sstu.grivvus.yamusic.data.local.TrackAlbumCrossRef
import sstu.grivvus.yamusic.data.local.TrackAlbumDao
import sstu.grivvus.yamusic.data.network.model.NetworkTrack
import sstu.grivvus.yamusic.data.network.model.TrackQuality
import sstu.grivvus.yamusic.data.network.model.UploadPart
import sstu.grivvus.yamusic.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.yamusic.di.DefaultDispatcher

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
    private val playlistRemoteDataSource: PlaylistRemoteDataSource,
    private val trackRemoteDataSource: TrackRemoteDataSource,
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
            val preparedCover = coverUri?.let(::prepareUploadFile)
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
                        nameIsLocalOverride = false,
                        tracksSeeded = true,
                    ),
                )
                buildLocalState()
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
        albumId: Long,
    ): MusicLibraryData = withContext(dispatcher) {
        val preparedTrack = prepareUploadFile(trackUri)
        try {
            val trackId = trackRemoteDataSource.uploadTrack(
                name = title,
                artistId = artistId,
                albumId = albumId,
                track = preparedTrack.toUploadPart(),
            )
            artistDao.upsert(
                Artist(
                    remoteId = artistId,
                    name = "",
                ),
            )
            val existingAlbum = albumDao.getById(albumId)
            albumDao.upsert(
                Album(
                    remoteId = albumId,
                    artistId = artistId,
                    name = existingAlbum?.name.orEmpty(),
                    coverUri = existingAlbum?.coverUri,
                ),
            )
            audioTrackDao.upsert(
                AudioTrack(
                    remoteId = trackId,
                    name = title,
                    artistId = artistId,
                ),
            )
            trackAlbumDao.insert(TrackAlbumCrossRef(trackId = trackId, albumId = albumId))
            val playlist = playlistDao.getById(playlistId)
                ?: throw IOException("Playlist was not found")
            playlistRemoteDataSource.addTrack(playlistId, trackId)
            playlistTrackDao.insert(
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
        val remoteTracks = trackRemoteDataSource.getMyTracks()
        val existingTracks = audioTrackDao.getAll().associateBy { it.remoteId }
        val existingAlbums = albumDao.getAll().associateBy { it.remoteId }

        artistDao.upsertAll(
            remoteTracks
                .map { track ->
                    Artist(
                        remoteId = track.artistId,
                        name = "",
                    )
                }
                .distinctBy { it.remoteId },
        )

        albumDao.upsertAll(
            remoteTracks
                .map { track ->
                    track.toAlbum(existingAlbums = existingAlbums)
                }
                .distinctBy { it.remoteId },
        )

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
        trackAlbumDao.insertAll(
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
            val tracksForPlaylist =
                playlistTrackDao.getTrackIdsForPlaylist(playlist.remoteId).mapNotNull { trackId ->
                    trackMap[trackId]
                }
            PlaylistBundle(playlist = playlist, tracks = tracksForPlaylist)
        }
        return MusicLibraryData(
            playlists = playlists,
            libraryTracks = libraryTracks,
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

    private fun NetworkTrack.toAlbum(
        existingAlbums: Map<Long, Album>,
    ): Album {
        val existingAlbum = existingAlbums[albumId]
        return Album(
            remoteId = albumId,
            artistId = artistId,
            name = existingAlbum?.name.orEmpty(),
            coverUri = coverUrl?.toUri() ?: existingAlbum?.coverUri,
        )
    }
}

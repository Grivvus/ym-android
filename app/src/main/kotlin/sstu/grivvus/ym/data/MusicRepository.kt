package sstu.grivvus.ym.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
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
import sstu.grivvus.ym.data.download.LocalTrackFileStore
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
import java.time.LocalDate
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
    private val localTrackFileStore: LocalTrackFileStore,
    @param:ApplicationContext private val context: Context,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun loadLibrary(
        refreshFromNetwork: Boolean,
        playlistFilters: PlaylistFilters = PlaylistFilters(),
    ): MusicLibraryData =
        withContext(dispatcher) {
            if (refreshFromNetwork) {
                syncRemoteState(playlistFilters)
            }
            buildLocalState(playlistFilters)
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

    suspend fun createArtist(name: String): Artist = withContext(dispatcher) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            throw IOException("Artist name is required")
        }

        artistDao.getAll().firstOrNull { artist ->
            artist.name.equals(normalizedName, ignoreCase = true)
        }?.let { existingArtist ->
            return@withContext existingArtist
        }

        val artistId = artistRemoteDataSource.createArtist(name = normalizedName)
        val remoteArtistMetadata = loadRemoteArtistMetadata(artistId)
        val artist = Artist(
            remoteId = artistId,
            name = remoteArtistMetadata?.name?.takeIf { it.isNotBlank() } ?: normalizedName,
            imageUri = remoteArtistMetadata?.imageUri,
        )
        artistDao.upsert(artist)
        artist
    }

    suspend fun loadAlbumsForArtist(
        artistId: Long,
        refreshFromNetwork: Boolean = true,
    ): List<Album> = withContext(dispatcher) {
        if (refreshFromNetwork) {
            val remoteArtist = artistRemoteDataSource.getArtist(artistId)
            val existingArtist = artistDao.getById(artistId)
            artistDao.upsert(
                Artist(
                    remoteId = remoteArtist.id,
                    name = remoteArtist.name,
                    imageUri = remoteArtist.coverUrl?.toUri() ?: existingArtist?.imageUri,
                ),
            )

            val existingAlbumsById = albumDao.getAll().associateBy { it.remoteId }
            val remoteAlbums = remoteArtist.albumIds
                .distinct()
                .mapNotNull { albumId ->
                    runCatching { albumRemoteDataSource.getAlbum(albumId) }.getOrNull()
                }
            if (remoteAlbums.isNotEmpty()) {
                val albumsToStore = mutableListOf<Album>()
                remoteAlbums.forEach { album ->
                    val existingAlbum = existingAlbumsById[album.id]
                    albumsToStore += Album(
                        remoteId = album.id,
                        artistId = artistId,
                        name = album.name,
                        coverUri = resolveAlbumCoverUri(
                            albumId = album.id,
                            existingCoverUri = existingAlbum?.coverUri,
                        ),
                        releaseYear = album.releaseYear,
                        releaseDate = album.releaseFullDate,
                    )
                }
                albumDao.upsertAll(
                    albumsToStore,
                )
            }
        }

        albumDao.getAll()
            .filter { album -> album.artistId == artistId }
            .sortedWith(compareBy<Album> { it.name.lowercase() }.thenBy { it.remoteId })
    }

    suspend fun createAlbum(
        artistId: Long,
        name: String,
        coverUri: Uri? = null,
        releaseYear: Int? = null,
        releaseDate: LocalDate? = null,
    ): Album = withContext(dispatcher) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            throw IOException("Album name is required")
        }

        albumDao.getAll().firstOrNull { album ->
            album.artistId == artistId && album.name.equals(normalizedName, ignoreCase = true)
        }?.let { existingAlbum ->
            return@withContext existingAlbum
        }

        val preparedCover = coverUri?.let(::prepareUploadFile)
        try {
            val albumId = albumRemoteDataSource.createAlbum(
                artistId = artistId,
                name = normalizedName,
                cover = preparedCover?.toUploadPart(),
                releaseYear = releaseYear,
                releaseFullDate = releaseDate,
            )
            val remoteAlbumMetadata = loadRemoteAlbumMetadata(
                albumId = albumId,
                useCoverRouteFallback = false,
            )
            val resolvedCoverUri = if (preparedCover != null) {
                serverInfoRepository.albumCoverUri(albumId, cacheBust = true).also { uri ->
                    warmImageCache(uri)
                }
            } else {
                remoteAlbumMetadata?.coverUri
            }
            val album = Album(
                remoteId = albumId,
                artistId = artistId,
                name = remoteAlbumMetadata?.name?.takeIf { it.isNotBlank() } ?: normalizedName,
                coverUri = resolvedCoverUri,
                releaseYear = remoteAlbumMetadata?.releaseYear ?: releaseYear,
                releaseDate = remoteAlbumMetadata?.releaseDate ?: releaseDate,
            )
            albumDao.upsert(album)
            album
        } finally {
            preparedCover?.file?.delete()
        }
    }

    suspend fun createPlaylist(name: String, coverUri: Uri?, isPublic: Boolean): MusicLibraryData =
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
                    isPublic = isPublic,
                    cover = preparedCover?.toUploadPart(),
                )
                val resolvedCoverUri = preparedCover?.let {
                    serverInfoRepository.playlistCoverUri(playlistId, cacheBust = true)
                        .also { uri -> warmImageCache(uri) }
                }
                playlistDao.upsert(
                    Playlist(
                        remoteId = playlistId,
                        name = name,
                        coverUri = resolvedCoverUri,
                        ownerRemoteId = authSessionManager.requireCurrentUser().remoteId,
                        playlistType = PlaylistType.OWNED,
                        canEdit = true,
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

    suspend fun deleteAlbum(albumId: Long): MusicLibraryData = withContext(dispatcher) {
        albumRemoteDataSource.deleteAlbum(albumId)
        albumDao.deleteById(albumId)
        buildLocalState()
    }

    suspend fun uploadAlbumCover(albumId: Long, coverUri: Uri): MusicLibraryData =
        withContext(dispatcher) {
            val preparedCover = prepareUploadFile(coverUri)
            try {
                albumRemoteDataSource.uploadCover(
                    albumId = albumId,
                    cover = preparedCover.toUploadPart(),
                )
            } finally {
                preparedCover.file.delete()
            }

            val album = albumDao.getById(albumId)
                ?: throw IOException("Album was not found")
            val resolvedCoverUri = serverInfoRepository.albumCoverUri(
                albumId = albumId,
                cacheBust = true,
            ).also { uri -> warmImageCache(uri) }
            albumDao.upsert(
                album.copy(
                    coverUri = resolvedCoverUri,
                ),
            )
            buildLocalState()
        }

    suspend fun deleteAlbumCover(albumId: Long): MusicLibraryData = withContext(dispatcher) {
        albumRemoteDataSource.deleteCover(albumId)
        val album = albumDao.getById(albumId)
            ?: throw IOException("Album was not found")
        albumDao.upsert(album.copy(coverUri = null))
        buildLocalState()
    }

    suspend fun deletePlaylist(playlistId: Long): MusicLibraryData = withContext(dispatcher) {
        requirePlaylistOwner(playlistId)
        playlistRemoteDataSource.deletePlaylist(playlistId)
        playlistDao.deleteById(playlistId)
        buildLocalState()
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String): MusicLibraryData =
        withContext(dispatcher) {
            val currentPlaylist = requireEditablePlaylist(playlistId)
            val updatedPlaylist = playlistRemoteDataSource.updatePlaylist(playlistId, newName)
            playlistDao.upsert(
                currentPlaylist.copy(
                    name = updatedPlaylist.name,
                    coverUri = currentPlaylist.coverUri,
                    nameIsLocalOverride = false,
                    tracksSeeded = true,
                ),
            )
            buildLocalState()
        }

    suspend fun uploadPlaylistCover(playlistId: Long, coverUri: Uri): MusicLibraryData =
        withContext(dispatcher) {
            requireEditablePlaylist(playlistId)
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
            val resolvedCoverUri = serverInfoRepository.playlistCoverUri(
                playlistId = playlistId,
                cacheBust = true,
            ).also { uri -> warmImageCache(uri) }
            playlistDao.upsert(
                playlist.copy(
                    coverUri = resolvedCoverUri,
                ),
            )
            buildLocalState()
        }

    suspend fun addTracksToPlaylist(
        playlistId: Long,
        trackIds: Collection<Long>,
    ): MusicLibraryData = withContext(dispatcher) {
        val playlist = requireEditablePlaylist(playlistId)
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

    suspend fun uploadTrackToLibrary(
        trackUri: Uri,
        title: String,
        artistId: Long,
        albumId: Long?,
        isSingle: Boolean,
        isGloballyAvailable: Boolean?,
    ): MusicLibraryData = withContext(dispatcher) {
        uploadTrackInternal(
            trackUri = trackUri,
            title = title,
            artistId = artistId,
            albumId = albumId,
            isSingle = isSingle,
            isGloballyAvailable = isGloballyAvailable,
        )
        buildLocalState()
    }

    suspend fun uploadTrackAndAddToPlaylist(
        playlistId: Long,
        trackUri: Uri,
        title: String,
        artistId: Long,
        albumId: Long?,
        isSingle: Boolean,
        isGloballyAvailable: Boolean?,
    ): MusicLibraryData = withContext(dispatcher) {
        val playlist = requireEditablePlaylist(playlistId)
        val trackId = uploadTrackInternal(
            trackUri = trackUri,
            title = title,
            artistId = artistId,
            albumId = albumId,
            isSingle = isSingle,
            isGloballyAvailable = isGloballyAvailable,
        )
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
    }

    suspend fun deleteTrack(trackId: Long): MusicLibraryData = withContext(dispatcher) {
        deleteTracks(trackIds = listOf(trackId))
    }

    suspend fun deleteTracks(trackIds: Collection<Long>): MusicLibraryData =
        withContext(dispatcher) {
            val distinctTrackIds = trackIds.distinct()
            if (distinctTrackIds.isEmpty()) {
                return@withContext buildLocalState()
            }
            distinctTrackIds.forEach { trackId ->
                trackRemoteDataSource.deleteTrack(trackId)
            }
            localTrackFileStore.deleteTrackFiles(distinctTrackIds)
            audioTrackDao.deleteByIds(distinctTrackIds)
            buildLocalState()
        }

    private suspend fun requireEditablePlaylist(playlistId: Long): Playlist {
        val playlist = playlistDao.getById(playlistId)
            ?: throw IOException("Playlist was not found")
        if (!playlist.canEdit) {
            throw PlaylistAccessDenied("Playlist cannot be edited")
        }
        return playlist
    }

    private suspend fun requirePlaylistOwner(playlistId: Long): Playlist {
        val playlist = playlistDao.getById(playlistId)
            ?: throw IOException("Playlist was not found")
        val currentUser = authSessionManager.requireCurrentUser()
        if (playlist.ownerRemoteId != currentUser.remoteId) {
            throw PlaylistAccessDenied("Only playlist owner can delete this playlist")
        }
        return playlist
    }

    private suspend fun syncRemoteState(playlistFilters: PlaylistFilters = PlaylistFilters()) {
        val existingArtists = artistRemoteDataSource.getAllArtists()

        artistDao.upsertAll(
            existingArtists.map {
                Artist(it.id, it.name, it.coverUrl?.toUri())
            }
        )

        val remoteTracks = trackRemoteDataSource.getMyTracks()
        val fetchedArtistIds = existingArtists.map { artist -> artist.id }.toSet()
        val missingArtistIds = remoteTracks
            .map { track -> track.artistId }
            .distinct()
            .filterNot { artistId -> artistId in fetchedArtistIds }
        val missingArtists = missingArtistIds.mapNotNull { artistId ->
            runCatching { artistRemoteDataSource.getArtist(artistId) }.getOrNull()
        }
        if (missingArtists.isNotEmpty()) {
            artistDao.upsertAll(
                missingArtists.map { artist ->
                    Artist(artist.id, artist.name, artist.coverUrl?.toUri())
                },
            )
        }

        val existingTracks = audioTrackDao.getAll().associateBy { it.remoteId }
        val remoteTrackIds = remoteTracks.map { it.id }.toSet()
        val downloadedTrackFiles = localTrackFileStore.findDownloadedFiles(remoteTrackIds)
        val artistIdsByAlbumId = remoteTracks.associate { track -> track.albumId to track.artistId }

        val existingAlbumsById = albumDao.getAll().associateBy { it.remoteId }
        val albumIds = remoteTracks.map { it.albumId }.distinct()
        val fetchedAlbums = mutableListOf<Album>()
        albumIds.forEach { albumId ->
            val album = runCatching { albumRemoteDataSource.getAlbum(albumId) }.getOrNull()
                ?: return@forEach
            val artistId = artistIdsByAlbumId[album.id] ?: return@forEach
            val existingAlbum = existingAlbumsById[album.id]
            fetchedAlbums += Album(
                remoteId = album.id,
                artistId = artistId,
                name = album.name,
                coverUri = resolveAlbumCoverUri(
                    albumId = album.id,
                    existingCoverUri = existingAlbum?.coverUri,
                ),
                releaseYear = album.releaseYear,
                releaseDate = album.releaseFullDate,
            )
        }
        if (fetchedAlbums.isNotEmpty()) {
            albumDao.upsertAll(fetchedAlbums)
        }
        val availableAlbumIds =
            (existingAlbumsById.keys + fetchedAlbums.map { it.remoteId }).toSet()

        val staleTrackIds = existingTracks.keys - remoteTrackIds
        if (staleTrackIds.isNotEmpty()) {
            audioTrackDao.deleteByIds(staleTrackIds.toList())
        }

        audioTrackDao.upsertAll(
            remoteTracks.map { track ->
                val existingTrack = existingTracks[track.id]
                val downloadedTrackFile = downloadedTrackFiles[track.id]
                AudioTrack(
                    remoteId = track.id,
                    name = track.name,
                    artistId = track.artistId,
                    durationMs = track.durationMs,
                    uriFast = track.qualityPresets[TrackQuality.FAST]?.toUri()
                        ?: existingTrack?.uriFast,
                    uriStandard = track.qualityPresets[TrackQuality.STANDARD]?.toUri()
                        ?: existingTrack?.uriStandard,
                    uriHigh = track.qualityPresets[TrackQuality.HIGH]?.toUri()
                        ?: existingTrack?.uriHigh,
                    uriLossless = track.qualityPresets[TrackQuality.LOSSLESS]?.toUri()
                        ?: existingTrack?.uriLossless,
                    localPath = downloadedTrackFile?.absolutePath,
                    isDownloaded = downloadedTrackFile != null,
                )
            },
        )
        trackAlbumDao.clearAll()
        trackAlbumDao.insertAll(
            remoteTracks.mapNotNull { track ->
                if (track.albumId !in availableAlbumIds) {
                    null
                } else {
                    TrackAlbumCrossRef(
                        trackId = track.id,
                        albumId = track.albumId,
                    )
                }
            },
        )

        val remotePlaylistSummaries = playlistRemoteDataSource.getAvailablePlaylists(playlistFilters)
        val remotePlaylists = remotePlaylistSummaries.map { summary ->
            playlistRemoteDataSource.getPlaylist(summary.id)
        }
        val existingPlaylists = playlistDao.getAll().associateBy { it.remoteId }
        val remoteIds = remotePlaylistSummaries.map { it.id }.toSet()

        existingPlaylists.values
            .filter { playlistFilters.includes(it.playlistType) }
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
                    playlistType = remotePlaylist.playlistType,
                    canEdit = remotePlaylist.canEdit,
                    nameIsLocalOverride = false,
                    ownerRemoteId = remotePlaylist.ownerRemoteId,
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
                    coverUri = resolvePlaylistCoverUri(
                        playlistId = playlistId,
                        existingCoverUri = localPlaylist.coverUri,
                    ),
                    tracksSeeded = true,
                ),
            )
        }
    }

    private suspend fun buildLocalState(
        playlistFilters: PlaylistFilters = PlaylistFilters(),
    ): MusicLibraryData {
        val artists = artistDao.getAll()
        val tracks = resolveLocalDownloadState(audioTrackDao.getAll())
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
        val playlists = playlistDao.getAll()
            .filter { playlist -> playlistFilters.includes(playlist.playlistType) }
            .map { playlist ->
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

    private suspend fun resolveLocalDownloadState(tracks: List<AudioTrack>): List<AudioTrack> {
        val downloadedTrackFiles = localTrackFileStore.findDownloadedFiles(
            tracks.map { track -> track.remoteId },
        )
        return tracks.map { track ->
            val downloadedTrackFile = downloadedTrackFiles[track.remoteId]
            val resolvedTrack = track.copy(
                localPath = downloadedTrackFile?.absolutePath,
                isDownloaded = downloadedTrackFile != null,
            )
            if (resolvedTrack.localPath != track.localPath ||
                resolvedTrack.isDownloaded != track.isDownloaded
            ) {
                audioTrackDao.upsert(resolvedTrack)
            }
            resolvedTrack
        }
    }

    private suspend fun uploadTrackInternal(
        trackUri: Uri,
        title: String,
        artistId: Long,
        albumId: Long?,
        isSingle: Boolean,
        isGloballyAvailable: Boolean?,
    ): Long {
        val preparedTrack = prepareUploadFile(trackUri)
        try {
            val trackId = trackRemoteDataSource.uploadTrack(
                name = title,
                artistId = artistId,
                albumId = albumId,
                isSingle = isSingle,
                isGloballyAvailable = isGloballyAvailable,
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
                val remoteAlbumMetadata = loadRemoteAlbumMetadata(
                    albumId = albumId,
                    existingCoverUri = existingAlbum?.coverUri,
                )
                albumDao.upsert(
                    Album(
                        remoteId = albumId,
                        artistId = artistId,
                        name = remoteAlbumMetadata?.name?.takeIf { it.isNotBlank() }
                            ?: existingAlbum?.name.orEmpty(),
                        coverUri = remoteAlbumMetadata?.coverUri ?: existingAlbum?.coverUri,
                        releaseYear = remoteAlbumMetadata?.releaseYear
                            ?: existingAlbum?.releaseYear,
                        releaseDate = remoteAlbumMetadata?.releaseDate
                            ?: existingAlbum?.releaseDate,
                    ),
                )
                trackAlbumDao.upsert(TrackAlbumCrossRef(trackId = trackId, albumId = albumId))
            }
            return trackId
        } finally {
            preparedTrack.file.delete()
        }
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

    private suspend fun loadRemoteAlbumMetadata(
        albumId: Long,
        existingCoverUri: Uri? = null,
        useCoverRouteFallback: Boolean = true,
    ): RemoteAlbumMetadata? {
        return runCatching {
            albumRemoteDataSource.getAlbum(albumId)
        }.getOrNull()?.let { album ->
            RemoteAlbumMetadata(
                name = album.name,
                coverUri = resolveAlbumCoverUri(
                    albumId = albumId,
                    existingCoverUri = existingCoverUri,
                    useCoverRouteFallback = useCoverRouteFallback,
                ),
                releaseYear = album.releaseYear,
                releaseDate = album.releaseFullDate,
            )
        }
    }

    private suspend fun resolveAlbumCoverUri(
        albumId: Long,
        existingCoverUri: Uri?,
        useCoverRouteFallback: Boolean = true,
    ): Uri? {
        val resolvedCoverUri = existingCoverUri ?: if (useCoverRouteFallback) {
            serverInfoRepository.albumCoverUri(albumId)
        } else {
            null
        }
        warmImageCache(resolvedCoverUri)
        return resolvedCoverUri
    }

    private suspend fun resolvePlaylistCoverUri(
        playlistId: Long,
        existingCoverUri: Uri?,
        useCoverRouteFallback: Boolean = true,
    ): Uri? {
        val resolvedCoverUri = existingCoverUri ?: if (useCoverRouteFallback) {
            serverInfoRepository.playlistCoverUri(playlistId)
        } else {
            null
        }
        warmImageCache(resolvedCoverUri)
        return resolvedCoverUri
    }

    private suspend fun warmImageCache(uri: Uri?) {
        if (uri == null) {
            return
        }
        runCatching {
            SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
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
        val releaseYear: Int?,
        val releaseDate: LocalDate?,
    )
}

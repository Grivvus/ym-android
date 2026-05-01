package sstu.grivvus.ym.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sstu.grivvus.ym.data.local.AppDatabase
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.Playlist
import sstu.grivvus.ym.data.download.LocalTrackFileStore
import sstu.grivvus.ym.data.network.model.NetworkAlbum
import sstu.grivvus.ym.data.network.model.NetworkArtist
import sstu.grivvus.ym.data.network.model.NetworkPlaylist
import sstu.grivvus.ym.data.network.model.NetworkPlaylistDetails
import sstu.grivvus.ym.data.network.model.NetworkPlaylistEmpty
import sstu.grivvus.ym.data.network.model.NetworkUser
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.remote.album.AlbumRemoteDataSource
import sstu.grivvus.ym.data.network.remote.artist.ArtistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.ym.data.network.remote.user.UserRemoteDataSource
import sstu.grivvus.ym.testutil.MainDispatcherRule
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var applicationContext: Context

    private val playlistRemoteDataSource = mockk<PlaylistRemoteDataSource>()
    private val trackRemoteDataSource = mockk<TrackRemoteDataSource>()
    private val artistRemoteDataSource = mockk<ArtistRemoteDataSource>()
    private val albumRemoteDataSource = mockk<AlbumRemoteDataSource>()
    private val userRemoteDataSource = mockk<UserRemoteDataSource>()
    private val serverInfoRepository = mockk<ServerInfoRepository>()
    private val authSessionManager = mockk<AuthSessionManager>()

    @Before
    fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(applicationContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPlaylist_whenSameOwnerAlreadyHasPlaylistWithSameName_throwsConflictAndSkipsRemoteCall() =
        runTest {
            database.playlistDao().upsert(
                Playlist(
                    remoteId = 10L,
                    ownerRemoteId = 42L,
                    name = "Favorites",
                ),
            )
            coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)

            val repository = createRepository()

            val error = expectThrows<PlaylistCreationConflict> {
                repository.createPlaylist("Favorites", null, isPublic = false)
            }

            assertThat(error).hasMessageThat().isEqualTo("Playlist with this name already exists")
            assertThat(database.playlistDao().getAll()).hasSize(1)
            coVerify(exactly = 0) { playlistRemoteDataSource.createPlaylist(any(), any(), any()) }
        }

    @Test
    fun createPlaylist_whenSameNameBelongsToAnotherOwner_allowsCreation() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 10L,
                ownerRemoteId = 99L,
                name = "Favorites",
            ),
        )
        coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)
        coEvery {
            playlistRemoteDataSource.createPlaylist("Favorites", true, null)
        } returns 11L

        val repository = createRepository()

        val data = repository.createPlaylist("Favorites", null, isPublic = true)

        assertThat(data.playlists).hasSize(2)
        assertThat(database.playlistDao().getByUserAndName(42L, "Favorites")).isEqualTo(
            Playlist(
                remoteId = 11L,
                ownerRemoteId = 42L,
                name = "Favorites",
                coverUri = null,
                nameIsLocalOverride = false,
                tracksSeeded = true,
            ),
        )
        coVerify(exactly = 1) { playlistRemoteDataSource.createPlaylist("Favorites", true, null) }
    }

    @Test
    fun loadLibrary_savesPlaylistOwnerTypeAndEditPermissionFromRemoteSummary() = runTest {
        val filters = PlaylistFilters(
            includeOwned = false,
            includeShared = true,
            includePublic = false,
        )
        coEvery { artistRemoteDataSource.getAllArtists() } returns emptyList()
        coEvery { trackRemoteDataSource.getMyTracks() } returns emptyList()
        coEvery { playlistRemoteDataSource.getAvailablePlaylists(filters) } returns listOf(
            NetworkPlaylist(
                id = 20L,
                name = "Shared playlist",
                ownerRemoteId = 99L,
                playlistType = PlaylistType.SHARED,
                canEdit = true,
            ),
        )
        coEvery { playlistRemoteDataSource.getPlaylist(20L) } returns NetworkPlaylistDetails(
            id = 20L,
            name = "Shared playlist",
            trackIds = emptyList(),
            sharedWithUserIds = emptyList(),
        )
        coEvery { serverInfoRepository.playlistCoverUri(20L) } returns
                Uri.parse("https://example.com/playlists/20/cover")

        val repository = createRepository()

        val data = repository.loadLibrary(refreshFromNetwork = true, playlistFilters = filters)

        assertThat(data.playlists).hasSize(1)
        assertThat(database.playlistDao().getById(20L)).isEqualTo(
            Playlist(
                remoteId = 20L,
                ownerRemoteId = 99L,
                name = "Shared playlist",
                coverUri = Uri.parse("https://example.com/playlists/20/cover"),
                playlistType = PlaylistType.SHARED,
                canEdit = true,
                nameIsLocalOverride = false,
                tracksSeeded = true,
            ),
        )
    }

    @Test
    fun loadLibrary_whenFiltersArePartial_doesNotDeletePlaylistsOutsideRequestedTypes() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 60L,
                ownerRemoteId = 42L,
                name = "Owned playlist",
                playlistType = PlaylistType.OWNED,
                canEdit = true,
            ),
        )
        database.playlistDao().upsert(
            Playlist(
                remoteId = 61L,
                ownerRemoteId = 99L,
                name = "Stale public playlist",
                playlistType = PlaylistType.PUBLIC,
                canEdit = false,
            ),
        )
        val filters = PlaylistFilters(
            includeOwned = false,
            includeShared = false,
            includePublic = true,
        )
        coEvery { artistRemoteDataSource.getAllArtists() } returns emptyList()
        coEvery { trackRemoteDataSource.getMyTracks() } returns emptyList()
        coEvery { playlistRemoteDataSource.getAvailablePlaylists(filters) } returns emptyList()

        val repository = createRepository()

        val data = repository.loadLibrary(refreshFromNetwork = true, playlistFilters = filters)

        assertThat(data.playlists).isEmpty()
        assertThat(database.playlistDao().getById(60L)).isNotNull()
        assertThat(database.playlistDao().getById(61L)).isNull()
    }

    @Test
    fun renamePlaylist_whenPlaylistCannotBeEdited_skipsRemoteCall() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 30L,
                ownerRemoteId = 99L,
                name = "Public playlist",
                playlistType = PlaylistType.PUBLIC,
                canEdit = false,
            ),
        )

        val repository = createRepository()

        val error = expectThrows<PlaylistAccessDenied> {
            repository.renamePlaylist(30L, "New name")
        }

        assertThat(error).hasMessageThat().isEqualTo("Playlist cannot be edited")
        coVerify(exactly = 0) { playlistRemoteDataSource.updatePlaylist(any(), any()) }
    }

    @Test
    fun renamePlaylist_whenSharedPlaylistCanBeEdited_updatesRemoteAndLocalState() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 40L,
                ownerRemoteId = 99L,
                name = "Shared playlist",
                playlistType = PlaylistType.SHARED,
                canEdit = true,
            ),
        )
        coEvery {
            playlistRemoteDataSource.updatePlaylist(40L, "New shared name")
        } returns NetworkPlaylistEmpty(id = 40L, name = "New shared name")

        val repository = createRepository()

        repository.renamePlaylist(40L, "New shared name")

        assertThat(database.playlistDao().getById(40L)?.name).isEqualTo("New shared name")
        coVerify(exactly = 1) {
            playlistRemoteDataSource.updatePlaylist(40L, "New shared name")
        }
    }

    @Test
    fun deletePlaylist_whenCurrentUserIsNotOwner_skipsRemoteCall() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 50L,
                ownerRemoteId = 99L,
                name = "Someone else's playlist",
                playlistType = PlaylistType.SHARED,
                canEdit = true,
            ),
        )
        coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)

        val repository = createRepository()

        val error = expectThrows<PlaylistAccessDenied> {
            repository.deletePlaylist(50L)
        }

        assertThat(error).hasMessageThat()
            .isEqualTo("Only playlist owner can delete this playlist")
        coVerify(exactly = 0) { playlistRemoteDataSource.deletePlaylist(any()) }
    }

    @Test
    fun loadPlaylistSharingInfo_whenCurrentUserIsOwner_mapsSharedAndAvailableUsers() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 70L,
                ownerRemoteId = 42L,
                name = "Owned playlist",
                playlistType = PlaylistType.OWNED,
                canEdit = true,
            ),
        )
        coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)
        coEvery { playlistRemoteDataSource.getPlaylist(70L) } returns NetworkPlaylistDetails(
            id = 70L,
            name = "Owned playlist",
            trackIds = emptyList(),
            sharedWithUserIds = listOf(77L),
        )
        coEvery { userRemoteDataSource.getAllUsers() } returns listOf(
            networkUser(42L, "owner"),
            networkUser(77L, "editor"),
            networkUser(88L, "listener"),
        )

        val repository = createRepository()

        val sharingInfo = repository.loadPlaylistSharingInfo(70L)

        assertThat(sharingInfo.sharedUsers).containsExactly(
            PlaylistSharingUser(id = 77L, username = "editor"),
        )
        assertThat(sharingInfo.availableUsers).containsExactly(
            PlaylistSharingUser(id = 88L, username = "listener"),
        )
    }

    @Test
    fun sharePlaylistAccess_whenCurrentUserIsOwner_callsRemoteAndReloadsSharingInfo() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 80L,
                ownerRemoteId = 42L,
                name = "Owned playlist",
                playlistType = PlaylistType.OWNED,
                canEdit = true,
            ),
        )
        coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)
        coEvery {
            playlistRemoteDataSource.sharePlaylist(80L, listOf(88L), true)
        } returns Unit
        coEvery { playlistRemoteDataSource.getPlaylist(80L) } returns NetworkPlaylistDetails(
            id = 80L,
            name = "Owned playlist",
            trackIds = emptyList(),
            sharedWithUserIds = listOf(88L),
        )
        coEvery { userRemoteDataSource.getAllUsers() } returns listOf(
            networkUser(42L, "owner"),
            networkUser(88L, "editor"),
        )

        val repository = createRepository()

        val sharingInfo = repository.sharePlaylistAccess(
            playlistId = 80L,
            userIds = listOf(88L),
            hasWritePermission = true,
        )

        assertThat(sharingInfo.sharedUsers).containsExactly(
            PlaylistSharingUser(id = 88L, username = "editor"),
        )
        coVerify(exactly = 1) {
            playlistRemoteDataSource.sharePlaylist(80L, listOf(88L), true)
        }
    }

    @Test
    fun revokePlaylistAccess_whenCurrentUserIsNotOwner_skipsRemoteCall() = runTest {
        database.playlistDao().upsert(
            Playlist(
                remoteId = 90L,
                ownerRemoteId = 99L,
                name = "Shared playlist",
                playlistType = PlaylistType.SHARED,
                canEdit = true,
            ),
        )
        coEvery { authSessionManager.requireCurrentUser() } returns currentUser(42L)

        val repository = createRepository()

        val error = expectThrows<PlaylistAccessDenied> {
            repository.revokePlaylistAccess(90L, 77L)
        }

        assertThat(error).hasMessageThat()
            .isEqualTo("Only playlist owner can delete this playlist")
        coVerify(exactly = 0) { playlistRemoteDataSource.revokePlaylistAccess(any(), any()) }
    }

    @Test
    fun createAlbum_savesReleaseMetadataReturnedByRemoteSource() = runTest {
        database.artistDao().upsert(
            Artist(
                remoteId = 7L,
                name = "Artist",
            ),
        )
        val releaseDate = LocalDate.of(2025, 3, 14)
        coEvery {
            albumRemoteDataSource.createAlbum(
                artistId = 7L,
                name = "Future Album",
                cover = null,
                releaseYear = 2025,
                releaseFullDate = releaseDate,
            )
        } returns 70L
        coEvery { albumRemoteDataSource.getAlbum(70L) } returns NetworkAlbum(
            id = 70L,
            name = "Future Album",
            releaseYear = 2025,
            releaseFullDate = releaseDate,
        )

        val repository = createRepository()

        val album = repository.createAlbum(
            artistId = 7L,
            name = "Future Album",
            releaseYear = 2025,
            releaseDate = releaseDate,
        )

        assertThat(album.releaseYear).isEqualTo(2025)
        assertThat(album.releaseDate).isEqualTo(releaseDate)
        assertThat(database.albumDao().getById(70L)).isEqualTo(album)
    }

    @Test
    fun loadAlbumsForArtist_mapsReleaseMetadataIntoLocalAlbums() = runTest {
        database.artistDao().upsert(
            Artist(
                remoteId = 9L,
                name = "Artist",
            ),
        )
        val releaseDate = LocalDate.of(2024, 9, 1)
        coEvery { artistRemoteDataSource.getArtist(9L) } returns NetworkArtist(
            id = 9L,
            name = "Artist",
            albumIds = listOf(90L),
        )
        coEvery { albumRemoteDataSource.getAlbum(90L) } returns NetworkAlbum(
            id = 90L,
            name = "Loaded Album",
            releaseYear = 2024,
            releaseFullDate = releaseDate,
        )
        coEvery { serverInfoRepository.albumCoverUri(90L) } returns
                Uri.parse("https://example.com/albums/90/cover")

        val repository = createRepository()

        val albums = repository.loadAlbumsForArtist(9L, refreshFromNetwork = true)

        assertThat(albums).hasSize(1)
        assertThat(albums.single().releaseYear).isEqualTo(2024)
        assertThat(albums.single().releaseDate).isEqualTo(releaseDate)
    }

    private fun createRepository(): MusicRepository {
        return MusicRepository(
            playlistDao = database.playlistDao(),
            audioTrackDao = database.audioTrackDao(),
            artistDao = database.artistDao(),
            albumDao = database.albumDao(),
            trackAlbumDao = database.trackAlbumDao(),
            playlistTrackDao = database.playlistTrackDao(),
            playlistRemoteDataSource = playlistRemoteDataSource,
            trackRemoteDataSource = trackRemoteDataSource,
            artistRemoteDataSource = artistRemoteDataSource,
            albumRemoteDataSource = albumRemoteDataSource,
            userRemoteDataSource = userRemoteDataSource,
            serverInfoRepository = serverInfoRepository,
            authSessionManager = authSessionManager,
            localTrackFileStore = LocalTrackFileStore(applicationContext, mainDispatcherRule.dispatcher),
            context = applicationContext,
            dispatcher = mainDispatcherRule.dispatcher,
        )
    }

    private fun networkUser(id: Long, username: String): NetworkUser {
        return NetworkUser(
            id = id,
            username = username,
            email = null,
        )
    }

    private fun currentUser(userId: Long): LocalUser {
        return LocalUser(
            remoteId = userId,
            username = "tester",
            email = "tester@example.com",
            access = "access-token",
            refresh = "refresh-token",
        )
    }

    private suspend inline fun <reified T : Throwable> expectThrows(
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) {
                return error
            }
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown")
    }
}

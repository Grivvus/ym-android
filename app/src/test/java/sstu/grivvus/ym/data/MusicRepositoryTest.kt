package sstu.grivvus.ym.data

import android.content.Context
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
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.Playlist
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.remote.album.AlbumRemoteDataSource
import sstu.grivvus.ym.data.network.remote.artist.ArtistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.ym.testutil.MainDispatcherRule

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
            serverInfoRepository = serverInfoRepository,
            authSessionManager = authSessionManager,
            context = applicationContext,
            dispatcher = mainDispatcherRule.dispatcher,
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

package sstu.grivvus.ym.data.network.auth

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sstu.grivvus.ym.data.download.LocalTrackFileStore
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.AppDatabase
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.local.AudioTrack
import sstu.grivvus.ym.data.local.LocalAccountDataInvalidator
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.Playlist
import sstu.grivvus.ym.data.local.PlaylistTrackCrossRef
import sstu.grivvus.ym.data.local.TrackAlbumCrossRef
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.testutil.MainDispatcherRule
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultAuthSessionManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var applicationContext: Context

    @Before
    fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(applicationContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        File(applicationContext.noBackupFilesDir, DOWNLOAD_DIRECTORY_NAME)
            .listFiles()
            ?.forEach { file -> file.delete() }
        database.close()
    }

    @Test
    fun startSessionAndLogout_updateSessionStateAndStorage() = runTest {
        val tokenRefresher = FakeTokenRefresher()
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)
        val session = NetworkSession(
            userId = 1L,
            accessToken = "old-access",
            refreshToken = "refresh-token",
        )

        advanceUntilIdle()
        sessionManager.startSession(session)

        assertThat(sessionManager.currentSessionOrNull()).isEqualTo(session)
        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Authenticated(session))

        sessionManager.logout()

        assertThat(sessionManager.currentSessionOrNull()).isNull()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.LOGOUT))
    }

    @Test
    fun logout_clearsAccountDataButKeepsDownloadedTrackFiles() = runTest {
        seedAccountScopedData()
        val downloadedTrackFile = createDownloadedTrackFile()
        val sessionManager = createSessionManager(FakeTokenRefresher(), backgroundScope)

        advanceUntilIdle()
        sessionManager.logout()

        assertAccountDataCleared()
        assertThat(downloadedTrackFile.exists()).isTrue()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.LOGOUT))
    }

    @Test
    fun startSession_whenDifferentUserExists_invalidatesPreviousAccountData() = runTest {
        seedAccountScopedData(userId = 1L)
        val downloadedTrackFile = createDownloadedTrackFile()
        val sessionManager = createSessionManager(FakeTokenRefresher(), backgroundScope)
        val newSession = NetworkSession(
            userId = 2L,
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )

        advanceUntilIdle()
        sessionManager.startSession(newSession)

        assertLibraryDataCleared()
        assertThat(database.userDao().getActiveUser()).isEqualTo(
            LocalUser(
                remoteId = 2L,
                username = "",
                email = null,
                access = "new-access",
                refresh = "new-refresh",
            ),
        )
        assertThat(downloadedTrackFile.exists()).isTrue()
        assertThat(sessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(newSession),
        )
    }

    @Test
    fun startSession_whenSameUserExists_preservesAccountDataAndProfileFields() = runTest {
        seedAccountScopedData(userId = 1L)
        val sessionManager = createSessionManager(FakeTokenRefresher(), backgroundScope)
        val refreshedSession = NetworkSession(
            userId = 1L,
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )

        advanceUntilIdle()
        sessionManager.startSession(refreshedSession)

        assertThat(database.userDao().getActiveUser()).isEqualTo(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "new-access",
                refresh = "new-refresh",
                avatarUri = Uri.parse("http://example.com/users/1/avatar"),
            ),
        )
        assertThat(database.artistDao().getAll()).hasSize(1)
        assertThat(database.albumDao().getAll()).hasSize(1)
        assertThat(database.audioTrackDao().getAll()).hasSize(1)
        assertThat(database.playlistDao().getAll()).hasSize(1)
        assertThat(database.trackAlbumDao().getAll()).hasSize(1)
        assertThat(database.playlistTrackDao().getTrackIdsForPlaylist(TEST_PLAYLIST_ID))
            .containsExactly(TEST_TRACK_ID)
    }

    @Test
    fun requireSession_withoutStoredSession_marksSessionExpired() = runTest {
        val sessionManager = createSessionManager(FakeTokenRefresher(), backgroundScope)

        advanceUntilIdle()
        val error = expectThrows<SessionExpiredException> {
            sessionManager.requireSession()
        }

        assertThat(error).hasMessageThat().contains("Session expired")
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
    }

    @Test
    fun refreshAfterUnauthorized_whenRefreshSucceeds_updatesTokensAndPreservesProfile() = runTest {
        val avatarUri = Uri.parse("http://example.com/users/1/avatar")
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
                avatarUri = avatarUri,
            ),
        )
        val tokenRefresher = FakeTokenRefresher(
            nextSession = NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
        )
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

        advanceUntilIdle()
        val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("old-access")

        assertThat(refreshedAccessToken).isEqualTo("new-access")
        assertThat(tokenRefresher.invocationCount).isEqualTo(1)
        assertThat(tokenRefresher.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(sessionManager.currentSessionOrNull()).isEqualTo(
            NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
        )
        assertThat(database.userDao().getActiveUser()).isEqualTo(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "new-access",
                refresh = "new-refresh",
                avatarUri = avatarUri,
            ),
        )
        assertThat(sessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(
                NetworkSession(
                    userId = 1L,
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            ),
        )
    }

    @Test
    fun refreshAfterUnauthorized_whenRefreshReturns401_expiresSession() = runTest {
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
            ),
        )
        val sessionManager = createSessionManager(
            FakeTokenRefresher(
                refreshError = UnauthorizedApiException(
                    message = "Unauthorized",
                    rawBody = """{"error":"token expired"}""",
                ),
            ),
            backgroundScope,
        )

        advanceUntilIdle()
        val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("old-access")

        assertThat(refreshedAccessToken).isNull()
        assertThat(database.userDao().getActiveUser()).isNull()
        assertThat(sessionManager.sessionState.value)
            .isEqualTo(SessionState.Unauthenticated(SessionEndReason.EXPIRED))
    }

    @Test
    fun refreshAfterUnauthorized_whenTokenWasAlreadyRotated_returnsCurrentTokenWithoutRefresh() =
        runTest {
            database.userDao().insert(
                LocalUser(
                    remoteId = 1L,
                    username = "tester",
                    email = "tester@example.com",
                    access = "fresh-access",
                    refresh = "refresh-token",
                ),
            )
            val tokenRefresher = FakeTokenRefresher(
                nextSession = NetworkSession(
                    userId = 1L,
                    accessToken = "new-access",
                    refreshToken = "new-refresh",
                ),
            )
            val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

            advanceUntilIdle()
            val refreshedAccessToken = sessionManager.refreshAfterUnauthorized("stale-access")

            assertThat(refreshedAccessToken).isEqualTo("fresh-access")
            assertThat(tokenRefresher.invocationCount).isEqualTo(0)
        }

    @Test
    fun refreshAfterUnauthorized_concurrentCalls_refreshOnlyOnce() = runTest {
        database.userDao().insert(
            LocalUser(
                remoteId = 1L,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "refresh-token",
            ),
        )
        val tokenRefresher = FakeTokenRefresher(
            nextSession = NetworkSession(
                userId = 1L,
                accessToken = "new-access",
                refreshToken = "new-refresh",
            ),
            refreshDelayMs = 50L,
        )
        val sessionManager = createSessionManager(tokenRefresher, backgroundScope)

        advanceUntilIdle()
        val firstCall = async { sessionManager.refreshAfterUnauthorized("old-access") }
        val secondCall = async { sessionManager.refreshAfterUnauthorized("old-access") }

        advanceUntilIdle()

        assertThat(firstCall.await()).isEqualTo("new-access")
        assertThat(secondCall.await()).isEqualTo("new-access")
        assertThat(tokenRefresher.invocationCount).isEqualTo(1)
    }

    private fun createSessionManager(
        tokenRefresher: FakeTokenRefresher,
        applicationScope: CoroutineScope,
    ): DefaultAuthSessionManager {
        return DefaultAuthSessionManager(
            userDao = database.userDao(),
            localAccountDataInvalidator = LocalAccountDataInvalidator(
                database = database,
                ioDispatcher = mainDispatcherRule.dispatcher,
            ),
            tokenRefresher = tokenRefresher,
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = applicationScope,
        )
    }

    private suspend fun seedAccountScopedData(
        userId: Long = 1L,
    ) {
        database.userDao().insert(
            LocalUser(
                remoteId = userId,
                username = "tester",
                email = "tester@example.com",
                access = "old-access",
                refresh = "old-refresh",
                avatarUri = Uri.parse("http://example.com/users/$userId/avatar"),
            ),
        )
        database.artistDao().upsert(Artist(remoteId = TEST_ARTIST_ID, name = "Artist"))
        database.albumDao().upsert(
            Album(
                remoteId = TEST_ALBUM_ID,
                artistId = TEST_ARTIST_ID,
                name = "Album",
            ),
        )
        database.audioTrackDao().upsert(
            AudioTrack(
                remoteId = TEST_TRACK_ID,
                artistId = TEST_ARTIST_ID,
                name = "Track",
            ),
        )
        database.playlistDao().upsert(
            Playlist(
                remoteId = TEST_PLAYLIST_ID,
                ownerRemoteId = userId,
                name = "Playlist",
            ),
        )
        database.trackAlbumDao().upsert(
            TrackAlbumCrossRef(trackId = TEST_TRACK_ID, albumId = TEST_ALBUM_ID),
        )
        database.playlistTrackDao().upsert(
            PlaylistTrackCrossRef(playlistId = TEST_PLAYLIST_ID, trackId = TEST_TRACK_ID),
        )
    }

    private suspend fun createDownloadedTrackFile(
        trackId: Long = TEST_TRACK_ID,
    ): File {
        return createLocalTrackFileStore()
            .finalFileFor(trackId, "audio/mpeg")
            .also { file -> file.writeText("downloaded audio") }
    }

    private fun createLocalTrackFileStore(): LocalTrackFileStore {
        return LocalTrackFileStore(
            applicationContext,
            mainDispatcherRule.dispatcher,
        )
    }

    private suspend fun assertAccountDataCleared() {
        assertThat(database.userDao().getActiveUser()).isNull()
        assertLibraryDataCleared()
    }

    private suspend fun assertLibraryDataCleared() {
        assertThat(database.artistDao().getAll()).isEmpty()
        assertThat(database.albumDao().getAll()).isEmpty()
        assertThat(database.audioTrackDao().getAll()).isEmpty()
        assertThat(database.playlistDao().getAll()).isEmpty()
        assertThat(database.trackAlbumDao().getAll()).isEmpty()
        assertThat(database.playlistTrackDao().getTrackIdsForPlaylist(TEST_PLAYLIST_ID)).isEmpty()
    }

    private class FakeTokenRefresher(
        private val nextSession: NetworkSession? = null,
        private val refreshError: Throwable? = null,
        private val refreshDelayMs: Long = 0L,
    ) : TokenRefresher {
        var invocationCount: Int = 0
            private set

        var lastRefreshToken: String? = null
            private set

        override suspend fun refresh(refreshToken: String): NetworkSession {
            invocationCount += 1
            lastRefreshToken = refreshToken
            if (refreshDelayMs > 0) {
                delay(refreshDelayMs)
            }
            refreshError?.let { throw it }
            return checkNotNull(nextSession)
        }
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

    private companion object {
        private const val TEST_ARTIST_ID = 10L
        private const val TEST_ALBUM_ID = 20L
        private const val TEST_TRACK_ID = 30L
        private const val TEST_PLAYLIST_ID = 40L
        private const val DOWNLOAD_DIRECTORY_NAME = "downloaded_tracks"
    }
}

package sstu.grivvus.ym.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sstu.grivvus.ym.data.local.AppDatabase
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.auth.SessionEndReason
import sstu.grivvus.ym.data.network.auth.SessionState
import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.data.network.remote.track.TrackDownloadRemoteDataSource
import sstu.grivvus.ym.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackDownloadManagerTest {
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
    fun sessionEnd_cancelsDownloadsButKeepsDownloadedTrackFiles() = runTest {
        val localTrackFileStore = LocalTrackFileStore(
            applicationContext,
            mainDispatcherRule.dispatcher,
        )
        val downloadedTrackFile = localTrackFileStore
            .finalFileFor(TEST_TRACK_ID, "audio/mpeg")
            .also { file -> file.writeText("downloaded audio") }
        val sessionState = MutableStateFlow<SessionState>(
            SessionState.Authenticated(
                NetworkSession(
                    userId = 1L,
                    accessToken = "access",
                    refreshToken = "refresh",
                ),
            ),
        )
        val authSessionManager = mockk<AuthSessionManager>()
        every { authSessionManager.sessionState } returns sessionState

        TrackDownloadManager(
            remoteDataSource = mockk<TrackDownloadRemoteDataSource>(),
            localTrackFileStore = localTrackFileStore,
            audioTrackDao = database.audioTrackDao(),
            authSessionManager = authSessionManager,
            applicationScope = backgroundScope,
            ioDispatcher = mainDispatcherRule.dispatcher,
        )

        advanceUntilIdle()
        sessionState.value = SessionState.Unauthenticated(SessionEndReason.LOGOUT)
        advanceUntilIdle()

        assertThat(downloadedTrackFile.exists()).isTrue()
    }

    private companion object {
        private const val TEST_TRACK_ID = 30L
        private const val DOWNLOAD_DIRECTORY_NAME = "downloaded_tracks"
    }
}

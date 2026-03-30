package sstu.grivvus.yamusic.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import sstu.grivvus.yamusic.data.local.AppDatabase
import sstu.grivvus.yamusic.data.local.ServerInfo
import sstu.grivvus.yamusic.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerInfoRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyStorage_usesDefaultConfig() = runTest {
        val repository = ServerInfoRepository(
            serverInfoDao = database.serverInfoDao(),
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = backgroundScope,
        )

        advanceUntilIdle()

        assertThat(repository.getServerInfo()).isNull()
        assertThat(repository.currentServerConfig()).isEqualTo(ServerConfig("10.0.2.2", "8000"))
        assertThat(repository.currentBaseUrl()).isEqualTo("http://10.0.2.2:8000")
    }

    @Test
    fun saveServerInfo_updatesStorageAndDerivedUrls() = runTest {
        val repository = ServerInfoRepository(
            serverInfoDao = database.serverInfoDao(),
            ioDispatcher = mainDispatcherRule.dispatcher,
            applicationScope = backgroundScope,
        )

        repository.saveServerInfo("example.com", "8081")

        assertThat(repository.getServerInfo()).isEqualTo(ServerInfo("example.com", "8081"))
        assertThat(repository.currentServerConfig()).isEqualTo(ServerConfig("example.com", "8081"))
        assertThat(repository.currentBaseUrl()).isEqualTo("http://example.com:8081")
        assertThat(repository.avatarUrl(42L)).isEqualTo("http://example.com:8081/users/42/avatar")
        assertThat(repository.playlistCoverUri(7L).toString())
            .isEqualTo("http://example.com:8081/playlists/7/cover")
    }
}

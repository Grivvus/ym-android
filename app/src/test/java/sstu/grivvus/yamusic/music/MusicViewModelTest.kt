package sstu.grivvus.yamusic.music

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import sstu.grivvus.yamusic.data.MusicLibraryData
import sstu.grivvus.yamusic.data.MusicRepository
import sstu.grivvus.yamusic.data.PlaylistCreationConflict
import sstu.grivvus.yamusic.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MusicRepository>()

    @Test
    fun createPlaylist_whenRepositoryReportsDuplicateName_showsConflictError() = runTest {
        coEvery { repository.loadLibrary(refreshFromNetwork = true) } returns emptyLibraryData()
        coEvery {
            repository.createPlaylist("Favorites", null)
        } throws PlaylistCreationConflict("Playlist with this name already exists")

        val viewModel = MusicViewModel(repository)
        advanceUntilIdle()

        viewModel.createPlaylist("Favorites", null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo("Playlist with this name already exists")
        assertThat(viewModel.uiState.value.isMutating).isFalse()
        assertThat(viewModel.uiState.value.playlists).isEmpty()
        coVerify(exactly = 1) { repository.createPlaylist("Favorites", null) }
    }

    private fun emptyLibraryData(): MusicLibraryData {
        return MusicLibraryData(
            playlists = emptyList(),
            libraryTracks = emptyList(),
        )
    }
}

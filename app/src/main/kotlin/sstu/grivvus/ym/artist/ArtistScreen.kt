package sstu.grivvus.ym.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToAlbum: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val artist = uiState.artist

    YMTheme {
        BottomNavScaffold(
            navigateToMusic = navigateToMusic,
            navigateToLibrary = navigateToLibrary,
            navigateToProfile = navigateToProfile,
            topBar = {
                TopAppBar(
                    title = { Text(artist?.name ?: "Artist") },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(appIcons.Sync, contentDescription = "fetch data from server")
                        }
                    },
                )
            },
        ) { innerPadding ->
            ScreenStateHost(
                isLoading = uiState.isLoading && artist == null,
                errorMessage = uiState.errorMessage,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.padding(innerPadding),
            ) {
                if (artist != null) {
                    ArtistDetails(
                        artist = artist,
                        isBusy = uiState.isRefreshing,
                        onAlbumClick = navigateToAlbum,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyStateCard(
                            title = "Artist unavailable",
                            description = "This artist could not be loaded from the current library state.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetails(
    artist: ArtistDetailUi,
    isBusy: Boolean,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Artwork(
                        uri = artist.imageUri,
                        modifier = Modifier.size(120.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${artist.albums.size} albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Refreshing artist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Albums",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (artist.albums.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No albums",
                    description = "No albums for this artist are available in the local library.",
                )
            }
        } else {
            items(artist.albums, key = { it.id }) { album ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            uri = album.coverUri,
                            modifier = Modifier.size(72.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${album.trackCount} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Open",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

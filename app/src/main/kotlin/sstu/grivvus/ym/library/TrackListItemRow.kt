package sstu.grivvus.ym.library

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sstu.grivvus.ym.R
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.resolve

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItemRow(
    name: String,
    subtitle: UiText,
    coverUri: Uri?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isBusy: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloading: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onDeleteLocalCopy: (() -> Unit)? = null,
    onAddToPlaylists: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isBusy,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    enabled = !isBusy,
                )
            }
            Artwork(
                uri = coverUri,
                modifier = Modifier
                    .padding(start = if (isSelectionMode) 8.dp else 0.dp)
                    .size(56.dp),
                cornerRadius = 12.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(
                                R.string.library_cd_track_available_offline,
                            ),
                            tint = OfflineAvailableColor,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (
                onDelete != null ||
                onDownload != null ||
                onDeleteLocalCopy != null ||
                onAddToPlaylists != null ||
                onGoToArtist != null ||
                onGoToAlbum != null
            ) {
                TrackOverflowMenu(
                    expanded = menuExpanded,
                    isBusy = isBusy,
                    isDownloading = isDownloading,
                    isDownloaded = isDownloaded,
                    canGoToAlbum = onGoToAlbum != null,
                    onExpandedChange = { menuExpanded = it },
                    onDelete = onDelete,
                    onDownload = onDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onAddToPlaylists = onAddToPlaylists,
                    onGoToArtist = onGoToArtist,
                    onGoToAlbum = onGoToAlbum,
                )
            }
        }
    }
}

@Composable
private fun TrackOverflowMenu(
    expanded: Boolean,
    isBusy: Boolean,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    canGoToAlbum: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onDeleteLocalCopy: (() -> Unit)?,
    onAddToPlaylists: (() -> Unit)?,
    onGoToArtist: (() -> Unit)?,
    onGoToAlbum: (() -> Unit)?,
) {
    Box {
        IconButton(
            onClick = { onExpandedChange(true) },
            enabled = !isBusy,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.library_cd_track_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            when {
                isDownloading -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_downloading_track)) },
                        enabled = false,
                        onClick = {},
                        leadingIcon = {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        },
                    )
                }

                isDownloaded && onDeleteLocalCopy != null -> {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.library_action_delete_local_track_copy))
                        },
                        onClick = {
                            onExpandedChange(false)
                            onDeleteLocalCopy()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                            )
                        },
                    )
                }

                onDownload != null -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_download_track)) },
                        onClick = {
                            onExpandedChange(false)
                            onDownload()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            onDelete?.let { delete ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_action_delete)) },
                    onClick = {
                        onExpandedChange(false)
                        delete()
                    },
                )
            }
            onAddToPlaylists?.let { addToPlaylists ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_action_add_to_playlists)) },
                    onClick = {
                        onExpandedChange(false)
                        addToPlaylists()
                    },
                )
            }
            onGoToArtist?.let { goToArtist ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_action_go_to_artist)) },
                    onClick = {
                        onExpandedChange(false)
                        goToArtist()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_action_go_to_album)) },
                enabled = canGoToAlbum,
                onClick = {
                    onExpandedChange(false)
                    onGoToAlbum?.invoke()
                },
            )
        }
    }
}

private val OfflineAvailableColor = Color(0xFF2E7D32)

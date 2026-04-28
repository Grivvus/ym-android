package sstu.grivvus.ym.library

import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiText

data class LibraryTrackItemUi(
    val id: Long,
    val name: String,
    val subtitle: UiText,
    val artistId: Long,
    val albumId: Long?,
    val isDownloaded: Boolean,
)

internal fun TrackBundle.toLibraryTrackItemUi(
    artistsById: Map<Long, Artist>,
): LibraryTrackItemUi {
    val primaryAlbum = albums.firstOrNull()
    val artistName = artistsById[track.artistId]?.let(::artistDisplayName)
    val albumName = primaryAlbum?.let(::albumDisplayName)
    return LibraryTrackItemUi(
        id = track.remoteId,
        name = track.name,
        subtitle = listOfNotNull(artistName, albumName)
            .takeIf { parts -> parts.isNotEmpty() }
            ?.let(UiText::Joined)
            ?: UiText.StringResource(R.string.common_placeholder_single),
        artistId = track.artistId,
        albumId = primaryAlbum?.remoteId,
        isDownloaded = track.isDownloaded,
    )
}

internal fun artistDisplayName(artist: Artist): UiText =
    artist.name.takeIf { it.isNotBlank() }?.asUiText()
        ?: UiText.StringResource(R.string.common_placeholder_artist_id, listOf(artist.remoteId))

internal fun albumDisplayName(album: Album): UiText =
    album.name.takeIf { it.isNotBlank() }?.asUiText()
        ?: UiText.StringResource(R.string.common_placeholder_album_id, listOf(album.remoteId))

internal fun albumDisplayReleaseYear(album: Album): Int? = album.releaseYear ?: album.releaseDate?.year

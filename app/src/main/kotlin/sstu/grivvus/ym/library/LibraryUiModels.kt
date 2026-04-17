package sstu.grivvus.ym.library

import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.Artist

data class LibraryTrackItemUi(
    val id: Long,
    val name: String,
    val subtitle: String,
    val artistId: Long,
    val albumId: Long?,
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
        subtitle = listOfNotNull(
            artistName?.takeIf { it.isNotBlank() },
            albumName?.takeIf { it.isNotBlank() },
        ).joinToString(" • ").ifBlank { "Single" },
        artistId = track.artistId,
        albumId = primaryAlbum?.remoteId,
    )
}

internal fun artistDisplayName(artist: Artist): String {
    return artist.name.ifBlank { "Artist #${artist.remoteId}" }
}

internal fun albumDisplayName(album: Album): String {
    return album.name.ifBlank { "Album #${album.remoteId}" }
}

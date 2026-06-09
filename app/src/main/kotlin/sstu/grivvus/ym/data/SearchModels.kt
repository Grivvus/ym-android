package sstu.grivvus.ym.data

enum class SearchType {
    TRACKS,
    ALBUMS,
    ARTISTS,
}

data class SearchResults(
    val query: String,
    val tracks: List<TrackSearchResult>,
    val albums: List<AlbumSearchResult>,
    val artists: List<ArtistSearchResult>,
)

data class TrackSearchResult(
    val trackId: Long,
    val name: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long,
    val albumName: String,
    val durationMs: Long?,
    val isGloballyAvailable: Boolean,
    val score: Float,
)

data class AlbumSearchResult(
    val albumId: Long,
    val albumName: String,
    val artistId: Long,
    val artistName: String,
    val releaseYear: Int?,
    val score: Float,
)

data class ArtistSearchResult(
    val artistId: Long,
    val artistName: String,
    val score: Float,
)

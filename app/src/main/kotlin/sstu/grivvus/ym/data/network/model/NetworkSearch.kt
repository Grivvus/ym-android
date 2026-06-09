package sstu.grivvus.ym.data.network.model

enum class NetworkSearchType {
    TRACKS,
    ALBUMS,
    ARTISTS,
}

data class NetworkSearchResults(
    val query: String,
    val tracks: List<NetworkTrackSearchResult>,
    val albums: List<NetworkAlbumSearchResult>,
    val artists: List<NetworkArtistSearchResult>,
)

data class NetworkTrackSearchResult(
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

data class NetworkAlbumSearchResult(
    val albumId: Long,
    val albumName: String,
    val artistId: Long,
    val artistName: String,
    val releaseYear: Int?,
    val score: Float,
)

data class NetworkArtistSearchResult(
    val artistId: Long,
    val artistName: String,
    val score: Float,
)

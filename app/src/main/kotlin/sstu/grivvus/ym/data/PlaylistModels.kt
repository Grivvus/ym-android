package sstu.grivvus.ym.data

enum class PlaylistType {
    OWNED,
    PUBLIC,
    SHARED,
}

data class PlaylistFilters(
    val includeOwned: Boolean = true,
    val includeShared: Boolean = true,
    val includePublic: Boolean = true,
) {
    val hasEnabledFilter: Boolean
        get() = includeOwned || includeShared || includePublic

    fun includes(type: PlaylistType): Boolean {
        return when (type) {
            PlaylistType.OWNED -> includeOwned
            PlaylistType.SHARED -> includeShared
            PlaylistType.PUBLIC -> includePublic
        }
    }
}

class PlaylistAccessDenied(
    message: String,
) : Exception(message)

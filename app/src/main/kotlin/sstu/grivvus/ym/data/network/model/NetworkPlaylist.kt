package sstu.grivvus.ym.data.network.model

import sstu.grivvus.ym.data.PlaylistType

data class NetworkPlaylist(
    val id: Long,
    val name: String,
    val ownerRemoteId: Long,
    val playlistType: PlaylistType,
    val canEdit: Boolean,
)

data class NetworkPlaylistDetails(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val sharedWithUserIds: List<Long>,
)

data class NetworkPlaylistEmpty(
    val id: Long,
    val name: String,
)

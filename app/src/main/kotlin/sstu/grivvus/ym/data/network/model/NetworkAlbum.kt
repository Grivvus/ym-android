package sstu.grivvus.ym.data.network.model

import java.time.LocalDate

data class NetworkAlbum(
    val id: Long,
    val name: String,
    val releaseYear: Int? = null,
    val releaseFullDate: LocalDate? = null,
    val trackIds: List<Long> = emptyList(),
)

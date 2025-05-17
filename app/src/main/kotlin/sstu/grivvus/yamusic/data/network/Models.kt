package sstu.grivvus.yamusic.data.network

import java.util.Date

data class NetworkUser (
    val id: Int,
    val email: String,
    val username: String,
    val password: String,

    // эти данные будут приходить из ручки, хотя тут они мне не нужны
//    val createdAt: Date,
//    val updatedAt: Date,
)
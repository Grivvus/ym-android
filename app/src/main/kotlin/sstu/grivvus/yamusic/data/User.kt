package sstu.grivvus.yamusic.data

data class User(
    val email: String?,
    val username: String,
    val password: String,
    val token: String?,
)
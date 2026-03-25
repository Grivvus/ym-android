package sstu.grivvus.yamusic.data.network.auth

interface AuthHeaderProvider {
    suspend fun authorizationHeaderOrNull(): String?

    fun authorizationHeaderOrNullBlocking(): String?
}

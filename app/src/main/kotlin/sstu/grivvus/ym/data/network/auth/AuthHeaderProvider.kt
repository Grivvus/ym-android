package sstu.grivvus.ym.data.network.auth

interface AuthHeaderProvider {
    suspend fun authorizationHeaderOrNull(): String?

    fun authorizationHeaderOrNullBlocking(): String?
}

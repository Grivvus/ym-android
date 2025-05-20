package sstu.grivvus.yamusic.data.network

import kotlinx.serialization.Serializable
import java.util.Date

abstract class NetworkUser

@Serializable
class NetworkUserCreate (
    val username: String,
    val email: String?,
    val password: String,
): NetworkUser()

@Serializable
class NetworkUserLogin(
    val username: String,
    val password: String,
): NetworkUser()

@Serializable
data class TokenResponse(
    val tokenType: String,
    val accessToken: String,
)
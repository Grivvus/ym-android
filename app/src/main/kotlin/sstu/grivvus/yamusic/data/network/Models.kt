package sstu.grivvus.yamusic.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkUserCreate(
    val username: String,
    val email: String?,
    val password: String,
)

@Serializable
data class NetworkUserLogin(
    val username: String,
    val password: String,
)

@Serializable
data class ChangeUserDto(
    val username: String,
    @SerialName("new_username")
    val newUsername: String?,
    @SerialName("new_email")
    val newEmail: String?
)

@Serializable
data class TokenResponse(
    @SerialName("user_id")
    val userId: Long,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
)

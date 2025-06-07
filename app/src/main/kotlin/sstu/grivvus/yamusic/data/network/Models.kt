package sstu.grivvus.yamusic.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.Date

@Serializable
data class NetworkUserCreate (
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
data class ChangePasswordDto(
    val username: String,
    @SerialName("current_password")
    val currentPassword: String,
    @SerialName("new_password")
    val newPassword: String,
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
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("access_token")
    val accessToken: String,
)

@Serializable
data class RemoteTrackReturn(
    val id: Long,
    val name: String,
    val artists: String?,
    val album: String,
    val url: String,
)

@Serializable
data class GetInitialTracksDto(
    val data: List<RemoteTrackReturn>
)

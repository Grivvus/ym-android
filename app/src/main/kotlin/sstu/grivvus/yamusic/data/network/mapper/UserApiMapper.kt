package sstu.grivvus.yamusic.data.network.mapper

import sstu.grivvus.yamusic.data.network.model.NetworkUser
import sstu.grivvus.yamusic.openapi.models.UserReturn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserApiMapper @Inject constructor() {
    fun mapUser(response: UserReturn): NetworkUser {
        return NetworkUser(
            id = response.id.toLong(),
            username = response.username,
            email = response.email,
        )
    }
}

package sstu.grivvus.yamusic.data.network.mapper

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.model.NetworkUser
import sstu.grivvus.yamusic.openapi.models.UserReturn

@Singleton
class UserApiMapper @Inject constructor() {
    fun mapUser(response: UserReturn): NetworkUser {
        return TODO("Map generated user response to internal user model")
    }
}

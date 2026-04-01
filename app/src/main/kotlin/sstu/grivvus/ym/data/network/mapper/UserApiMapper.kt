package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkUser
import sstu.grivvus.ym.openapi.models.UserReturn
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

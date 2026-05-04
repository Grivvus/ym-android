package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkUser
import sstu.grivvus.ym.openapi.models.SimpleUser
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
            isSuperuser = response.isSuperuser,
        )
    }

    fun mapSimpleUser(response: SimpleUser): NetworkUser {
        return NetworkUser(
            id = response.id.toLong(),
            username = response.username,
            email = null,
        )
    }

    fun mapSimpleUsers(response: List<SimpleUser>): List<NetworkUser> {
        return response.map(::mapSimpleUser)
    }
}

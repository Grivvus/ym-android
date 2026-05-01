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
            isSuperuser = extractIsSuperuser(response),
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

    private fun extractIsSuperuser(response: UserReturn): Boolean {
        // The generated model in this repo may temporarily lag behind the remote spec.
        val getter = response.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 0 && (method.name == "isSuperuser" || method.name == "getIsSuperuser")
        } ?: return false
        return getter.invoke(response) as? Boolean ?: false
    }
}

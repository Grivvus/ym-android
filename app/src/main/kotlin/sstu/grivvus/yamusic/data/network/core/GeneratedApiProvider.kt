package sstu.grivvus.yamusic.data.network.core

import sstu.grivvus.yamusic.openapi.apis.DefaultApi

interface GeneratedApiProvider {
    suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T

    suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T
}

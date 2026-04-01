package sstu.grivvus.ym.data.network.core

import sstu.grivvus.ym.openapi.apis.DefaultApi

interface GeneratedApiProvider {
    suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T

    suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T
}

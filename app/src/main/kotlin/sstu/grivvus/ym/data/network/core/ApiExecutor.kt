package sstu.grivvus.ym.data.network.core

import sstu.grivvus.ym.openapi.infrastructure.ApiResponse

interface ApiExecutor {
    suspend fun <T : Any> execute(block: suspend () -> ApiResponse<T?>): T

    suspend fun executeUnit(block: suspend () -> ApiResponse<Unit?>)

    suspend fun <T> executeRaw(block: suspend () -> T): T
}

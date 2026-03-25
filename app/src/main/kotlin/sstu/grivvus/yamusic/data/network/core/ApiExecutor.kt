package sstu.grivvus.yamusic.data.network.core

interface ApiExecutor {
    suspend fun <T> execute(block: suspend () -> T): T

    suspend fun executeUnit(block: suspend () -> Unit)
}

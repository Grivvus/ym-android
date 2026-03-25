package sstu.grivvus.yamusic.data.network.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultApiExecutor @Inject constructor(
    private val networkLogger: NetworkLogger,
    private val errorBodyParser: ErrorBodyParser,
) : ApiExecutor {
    override suspend fun <T> execute(block: suspend () -> T): T {
        return TODO("Implement common API execution pipeline")
    }

    override suspend fun executeUnit(block: suspend () -> Unit) {
        TODO("Implement common API execution pipeline for Unit responses")
    }
}

package sstu.grivvus.yamusic.data.network.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Test
import sstu.grivvus.yamusic.openapi.infrastructure.ClientError
import sstu.grivvus.yamusic.openapi.infrastructure.ClientException
import sstu.grivvus.yamusic.openapi.infrastructure.ServerError
import sstu.grivvus.yamusic.openapi.infrastructure.Success
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultApiExecutorTest {
    private val networkLogger = RecordingNetworkLogger()
    private val executor = DefaultApiExecutor(
        networkLogger = networkLogger,
        errorBodyParser = DefaultErrorBodyParser(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun execute_successResponse_returnsBody() = runTest {
        val result = executor.execute {
            Success("payload", statusCode = 200)
        }

        assertThat(result).isEqualTo("payload")
        assertThat(networkLogger.loggedErrors).isEmpty()
    }

    @Test
    fun execute_clientError401_mapsToUnauthorizedApiException() = runTest {
        val error = expectThrows<UnauthorizedApiException> {
            executor.execute<String> {
                ClientError<String?>(
                    message = "Unauthorized",
                    body = """{"error":"session expired"}""",
                    statusCode = 401,
                )
            }
        }

        assertThat(error.message).isEqualTo("session expired")
        assertThat(error.statusCode).isEqualTo(401)
        assertThat(error.rawBody).isEqualTo("""{"error":"session expired"}""")
        assertThat(networkLogger.loggedErrors).hasSize(1)
    }

    @Test
    fun execute_serverError_mapsToServerApiException() = runTest {
        val error = expectThrows<ServerApiException> {
            executor.execute<String> {
                ServerError<String?>(
                    message = "Server exploded",
                    body = """{"error":"try later"}""",
                    statusCode = 503,
                    headers = emptyMap(),
                )
            }
        }

        assertThat(error.message).isEqualTo("try later")
        assertThat(error.statusCode).isEqualTo(503)
        assertThat(networkLogger.loggedErrors).hasSize(1)
    }

    @Test
    fun executeRaw_clientException_mapsNestedClientErrorBody() = runTest {
        val error = expectThrows<NotFoundApiException> {
            executor.executeRaw<Unit> {
                throw ClientException(
                    message = "Client error : 404 Not Found",
                    statusCode = 404,
                    response = ClientError<String>(
                        message = "Not Found",
                        body = """{"error":"track not found"}""",
                        statusCode = 404,
                    ),
                )
            }
        }

        assertThat(error.statusCode).isEqualTo(404)
        assertThat(error.message).isEqualTo("track not found")
        assertThat(error.rawBody).isEqualTo("""{"error":"track not found"}""")
    }

    @Test
    fun execute_clientError_withNonContractBody_mapsToServerApiException() = runTest {
        val error = expectThrows<ServerApiException> {
            executor.execute<String> {
                ClientError<String?>(
                    message = "Not Found",
                    body = """{"message":"unexpected shape"}""",
                    statusCode = 404,
                )
            }
        }

        assertThat(error.statusCode).isEqualTo(404)
        assertThat(error.message).isEqualTo("Error response does not match API contract")
        assertThat(error.rawBody).isEqualTo("""{"message":"unexpected shape"}""")
    }

    @Test
    fun executeRaw_ioException_mapsToNetworkUnavailableException() = runTest {
        val error = expectThrows<NetworkUnavailableException> {
            executor.executeRaw<Unit> {
                throw IOException("socket timeout")
            }
        }

        assertThat(error.message).isEqualTo("socket timeout")
        assertThat(networkLogger.loggedErrors).hasSize(1)
    }

    @Test
    fun executeRaw_serializationException_mapsToSerializationApiException() = runTest {
        val error = expectThrows<SerializationApiException> {
            executor.executeRaw<Unit> {
                throw SerializationException("Bad JSON")
            }
        }

        assertThat(error.message).isEqualTo("Bad JSON")
        assertThat(networkLogger.loggedErrors).hasSize(1)
    }

    private class RecordingNetworkLogger : NetworkLogger {
        val loggedErrors = mutableListOf<Throwable>()

        override fun logRequest(method: String, path: String, body: String?) = Unit

        override fun logResponse(method: String, path: String, statusCode: Int, body: String?) =
            Unit

        override fun logError(method: String, path: String, throwable: Throwable) {
            loggedErrors += throwable
        }
    }

    private suspend inline fun <reified T : Throwable> expectThrows(
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) {
                return error
            }
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown")
    }
}

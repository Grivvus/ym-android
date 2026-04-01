package sstu.grivvus.ym.data.network.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import sstu.grivvus.ym.di.IoDispatcher
import sstu.grivvus.ym.openapi.infrastructure.ApiResponse
import sstu.grivvus.ym.openapi.infrastructure.ClientError
import sstu.grivvus.ym.openapi.infrastructure.ClientException
import sstu.grivvus.ym.openapi.infrastructure.Informational
import sstu.grivvus.ym.openapi.infrastructure.Redirection
import sstu.grivvus.ym.openapi.infrastructure.ServerError
import sstu.grivvus.ym.openapi.infrastructure.ServerException
import sstu.grivvus.ym.openapi.infrastructure.Success
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultApiExecutor @Inject constructor(
    private val networkLogger: NetworkLogger,
    private val errorBodyParser: ErrorBodyParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ApiExecutor {
    override suspend fun <T : Any> execute(block: suspend () -> ApiResponse<T?>): T {
        return executeRaw {
            when (val response = block()) {
                is Success -> {
                    response.data
                        ?: throw SerializationApiException("HTTP ${response.statusCode}: empty response body")
                }

                is ClientError -> throw response.toApiException()
                is ServerError -> throw response.toApiException()
                is Informational -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = response.statusText.ifBlank { "Unexpected informational response" },
                )

                is Redirection -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = "Unexpected redirection response",
                )

                else -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = "Unexpected API response",
                )
            }
        }
    }

    override suspend fun executeUnit(block: suspend () -> ApiResponse<Unit?>) {
        executeRaw {
            when (val response = block()) {
                is Success -> Unit
                is ClientError -> throw response.toApiException()
                is ServerError -> throw response.toApiException()
                is Informational -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = response.statusText.ifBlank { "Unexpected informational response" },
                )

                is Redirection -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = "Unexpected redirection response",
                )

                else -> throw ClientApiException(
                    statusCode = response.statusCode,
                    message = "Unexpected API response",
                )
            }
        }
    }

    override suspend fun <T> executeRaw(block: suspend () -> T): T {
        return withContext(ioDispatcher) {
            try {
                block()
            } catch (error: ApiException) {
                networkLogger.logError("API", "core", error)
                throw error
            } catch (error: ClientException) {
                val apiException = toClientApiException(error)
                networkLogger.logError("API", "core", apiException)
                throw apiException
            } catch (error: ServerException) {
                val apiException = toServerApiException(error)
                networkLogger.logError("API", "core", apiException)
                throw apiException
            } catch (error: SerializationException) {
                val apiException = SerializationApiException(
                    message = error.message ?: "Failed to serialize or deserialize API payload",
                    cause = error,
                )
                networkLogger.logError("API", "core", apiException)
                throw apiException
            } catch (error: IOException) {
                val apiException = NetworkUnavailableException(
                    message = error.message ?: "Network request failed",
                    cause = error,
                )
                networkLogger.logError("API", "core", apiException)
                throw apiException
            }
        }
    }

    private fun ClientError<*>.toApiException(): ApiException {
        val rawBody = body?.toString()
        val parsedMessage = parseErrorMessageOrThrow(rawBody = rawBody, statusCode = statusCode)
        return when (statusCode) {
            401 -> {
                UnauthorizedApiException(
                    message = parsedMessage ?: message?.ifBlank { null } ?: "Unauthorized",
                    rawBody = rawBody,
                )
            }

            404 -> {
                NotFoundApiException(
                    message = parsedMessage ?: message?.ifBlank { null } ?: "Not Found",
                    rawBody = rawBody,
                )
            }

            409 -> {
                ConflictApiException(
                    message = parsedMessage ?: message?.ifBlank { null } ?: "Conflict",
                    rawBody = rawBody,
                )
            }

            else -> {
                ClientApiException(
                    statusCode = statusCode,
                    message = parsedMessage ?: message?.ifBlank { null } ?: "Client request failed",
                    rawBody = rawBody,
                )
            }
        }
    }

    private fun ServerError<*>.toApiException(): ApiException {
        val rawBody = body?.toString()
        val parsedMessage = parseErrorMessageOrThrow(rawBody = rawBody, statusCode = statusCode)
        return ServerApiException(
            statusCode = statusCode,
            message = parsedMessage ?: message?.ifBlank { null } ?: "Server request failed",
            rawBody = rawBody,
        )
    }

    private fun toClientApiException(error: ClientException): ApiException {
        val rawBody = (error.response as? ClientError<*>)?.body?.toString()
        val parsedMessage = parseErrorMessageOrThrow(
            rawBody = rawBody,
            statusCode = error.statusCode,
        )
        return when (error.statusCode) {
            401 -> UnauthorizedApiException(
                message = parsedMessage ?: error.message?.ifBlank { null } ?: "Unauthorized",
                rawBody = rawBody,
                cause = error,
            )

            404 -> {
                NotFoundApiException(
                    message = parsedMessage ?: error.message?.ifBlank { null } ?: "Not Found",
                    rawBody = rawBody,
                    cause = error,
                )
            }

            409 -> {
                ConflictApiException(
                    message = parsedMessage ?: error.message?.ifBlank { null } ?: "Conflict",
                    rawBody = rawBody,
                    cause = error,
                )
            }

            else -> {
                ClientApiException(
                    statusCode = error.statusCode.coerceAtLeast(0),
                    message = parsedMessage ?: error.message?.ifBlank { null }
                    ?: "Client request failed",
                    rawBody = rawBody,
                    cause = error,
                )
            }
        }
    }

    private fun toServerApiException(error: ServerException): ApiException {
        val rawBody = (error.response as? ServerError<*>)?.body?.toString()
        val parsedMessage = parseErrorMessageOrThrow(
            rawBody = rawBody,
            statusCode = error.statusCode,
        )
        return ServerApiException(
            statusCode = error.statusCode.coerceAtLeast(0),
            message = parsedMessage ?: error.message?.ifBlank { null } ?: "Server request failed",
            rawBody = rawBody,
            cause = error,
        )
    }

    private fun parseErrorMessageOrThrow(rawBody: String?, statusCode: Int): String? {
        return try {
            errorBodyParser.parseMessage(rawBody)
        } catch (error: SerializationApiException) {
            throw ServerApiException(
                statusCode = statusCode.coerceAtLeast(0),
                message = "Error response does not match API contract",
                rawBody = rawBody,
                cause = error,
            )
        }
    }
}

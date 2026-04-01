package sstu.grivvus.ym.data.network.core

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.ym.openapi.infrastructure.Serializer
import sstu.grivvus.ym.openapi.models.ErrorResponse

@Singleton
class DefaultErrorBodyParser @Inject constructor() : ErrorBodyParser {
    override fun parseMessage(rawBody: String?): String? {
        val normalizedBody = rawBody?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            Serializer.kotlinxSerializationJson
                .decodeFromString<ErrorResponse>(normalizedBody)
                .error
                .trim()
                .ifEmpty {
                    throw SerializationApiException("Error response does not match API contract")
                }
        } catch (error: SerializationApiException) {
            throw SerializationApiException(
                message = "Error response does not match API contract",
                cause = error,
            )
        } catch (error: IllegalArgumentException) {
            throw SerializationApiException(
                message = "Error response does not match API contract",
                cause = error,
            )
        }
    }
}

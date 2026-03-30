package sstu.grivvus.yamusic.data.network.core

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import sstu.grivvus.yamusic.openapi.infrastructure.Serializer
import sstu.grivvus.yamusic.openapi.models.ErrorResponse

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

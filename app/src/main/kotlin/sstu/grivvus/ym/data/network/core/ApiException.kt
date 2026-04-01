package sstu.grivvus.ym.data.network.core

import java.io.IOException

sealed class ApiException(
    open val statusCode: Int?,
    override val message: String,
    open val rawBody: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

class ClientApiException(
    override val statusCode: Int,
    override val message: String,
    override val rawBody: String? = null,
    cause: Throwable? = null,
) : ApiException(statusCode = statusCode, message = message, rawBody = rawBody, cause = cause)

class UnauthorizedApiException(
    override val message: String = "Unauthorized",
    override val rawBody: String? = null,
    cause: Throwable? = null,
) : ApiException(statusCode = 401, message = message, rawBody = rawBody, cause = cause)

class NotFoundApiException(
    override val message: String = "Not found",
    override val rawBody: String? = null,
    cause: Throwable? = null,
) : ApiException(statusCode = 404, message = message, rawBody = rawBody, cause = cause)

class ConflictApiException(
    override val message: String = "Conflict",
    override val rawBody: String? = null,
    cause: Throwable? = null,
) : ApiException(statusCode = 409, message = message, rawBody = rawBody, cause = cause)

class ServerApiException(
    override val statusCode: Int,
    override val message: String,
    override val rawBody: String? = null,
    cause: Throwable? = null,
) : ApiException(statusCode = statusCode, message = message, rawBody = rawBody, cause = cause)

class NetworkUnavailableException(
    override val message: String,
    cause: Throwable? = null,
) : ApiException(statusCode = null, message = message, rawBody = null, cause = cause)

class SerializationApiException(
    override val message: String,
    cause: Throwable? = null,
) : ApiException(statusCode = null, message = message, rawBody = null, cause = cause)

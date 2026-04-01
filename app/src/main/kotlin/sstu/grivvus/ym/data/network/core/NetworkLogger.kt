package sstu.grivvus.ym.data.network.core

interface NetworkLogger {
    fun logRequest(method: String, path: String, body: String? = null)

    fun logResponse(method: String, path: String, statusCode: Int, body: String? = null)

    fun logError(method: String, path: String, throwable: Throwable)
}

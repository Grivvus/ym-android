package sstu.grivvus.yamusic.data.network.core

import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val NETWORK_LOG_TAG = "Network"

@Singleton
class TimberNetworkLogger @Inject constructor() : NetworkLogger {
    override fun logRequest(method: String, path: String, body: String?) {
        val details = body?.let { "\nRequest body: $it" }.orEmpty()
        Timber.tag(NETWORK_LOG_TAG).i("%s %s%s", method, path, details)
    }

    override fun logResponse(method: String, path: String, statusCode: Int, body: String?) {
        val details = body?.let { "\nResponse body: $it" }.orEmpty()
        Timber.tag(NETWORK_LOG_TAG).i("%s %s -> %d%s", method, path, statusCode, details)
    }

    override fun logError(method: String, path: String, throwable: Throwable) {
        Timber.tag(NETWORK_LOG_TAG)
            .e(throwable, "%s %s -> %s: %s", method, path, throwable::class.simpleName, throwable.message)
    }
}

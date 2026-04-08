package sstu.grivvus.ym

import timber.log.Timber

fun Throwable.logHandledException(source: String) {
    Timber.tag("EXCEPTION_LOG").e(this, "%s handled error", source)
}

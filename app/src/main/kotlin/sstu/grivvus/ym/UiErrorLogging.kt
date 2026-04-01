package sstu.grivvus.ym

import timber.log.Timber

fun Throwable.logHandledUiError(source: String) {
    Timber.tag("UiError").e(this, "%s handled error", source)
}

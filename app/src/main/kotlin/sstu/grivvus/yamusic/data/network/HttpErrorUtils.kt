package sstu.grivvus.yamusic.data.network

private val httpCodeRegex = Regex("""\bHTTP\s+(\d{3})\b""")

fun Throwable.httpStatusCodeOrNull(): Int? {
    val messageText = message ?: return null
    return httpCodeRegex.find(messageText)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

fun Throwable.isServerSideError(): Boolean = (httpStatusCodeOrNull() ?: 0) in 500..599

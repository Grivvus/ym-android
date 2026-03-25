package sstu.grivvus.yamusic.data.network.core

interface ErrorBodyParser {
    fun parseMessage(rawBody: String?): String?
}

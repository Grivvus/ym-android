package sstu.grivvus.ym.data.network.core

interface ErrorBodyParser {
    fun parseMessage(rawBody: String?): String?
}

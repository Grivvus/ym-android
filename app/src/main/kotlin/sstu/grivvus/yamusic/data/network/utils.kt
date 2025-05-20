package sstu.grivvus.yamusic.data.network

import kotlinx.serialization.json.Json

fun responseToToken(responseBody: String): TokenResponse {
    return Json.decodeFromString<TokenResponse>(responseBody)
}
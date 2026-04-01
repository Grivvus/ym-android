package sstu.grivvus.ym.data.network.model

data class StreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val contentType: String? = null,
    val contentLength: Long? = null,
)

package sstu.grivvus.yamusic.data.network.model

import java.io.File

data class UploadPart(
    val file: File,
    val mimeType: String? = null,
    val fileName: String = file.name,
)

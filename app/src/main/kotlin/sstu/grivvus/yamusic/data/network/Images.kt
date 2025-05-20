package sstu.grivvus.yamusic.data.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import sstu.grivvus.yamusic.Settings
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun getLocalProfilePicture(
    context: Context,
    fileName: String = "ProfilePicture"
):  Bitmap?{
    val appDirectory = context.getExternalFilesDir(
        Environment.DIRECTORY_PICTURES
    )

    val imageFile = File(appDirectory, "$fileName.jpg")

    if (!imageFile.exists()) {
        return null
    }
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
    return bitmap
}

fun storeImageLocally(context: Context, bitmap: Bitmap, imageName: String) {
    val appDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (appDirectory != null && !appDirectory.exists()) {
        appDirectory.mkdirs()
    }

    val imageFile = File(appDirectory, "$imageName.jpg")

    FileOutputStream(imageFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }
}
suspend fun loadImageFromExternal(username: String): Bitmap? {
    val client = OkHttpClient()
    val urlString = "${Settings.apiHost}:${Settings.apiPort}/getProfilePicture/$username"
    TODO("possible missing jwt token in header request")
    val request = Request.Builder()
        .url(urlString)
        .build()
    return withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                BitmapFactory.decodeStream(inputStream)
            } else {
                null
            }
        }
    }
}
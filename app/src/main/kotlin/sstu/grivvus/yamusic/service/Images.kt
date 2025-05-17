package sstu.grivvus.yamusic.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

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
fun loadImageFromExternal(): Bitmap {

}
package sstu.grivvus.yamusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class MusicApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        initCoroutineExceptionHandler()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthenticatedApiMediaInterceptor(applicationContext))
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(imageHttpClient))
            }
            .build()
    }

    private fun initCoroutineExceptionHandler() {
        CoroutineScope(Dispatchers.IO).launch {
            CoroutineExceptionHandler { _, _ -> }
        }
    }
}

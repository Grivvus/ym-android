package sstu.grivvus.yamusic

import android.app.Application
import android.content.pm.ApplicationInfo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import sstu.grivvus.yamusic.data.network.auth.AuthenticatedMediaInterceptor
import timber.log.Timber

@HiltAndroidApp
class MusicApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            MusicApplicationEntryPoint::class.java,
        )
        val imageHttpClient = OkHttpClient.Builder()
            .addInterceptor(entryPoint.authenticatedMediaInterceptor())
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(imageHttpClient))
            }
            .build()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MusicApplicationEntryPoint {
        fun authenticatedMediaInterceptor(): AuthenticatedMediaInterceptor
    }
}

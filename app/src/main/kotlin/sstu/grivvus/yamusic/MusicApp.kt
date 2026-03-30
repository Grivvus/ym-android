package sstu.grivvus.yamusic

import android.app.Application
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
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.network.AuthSessionManager

@HiltAndroidApp
class MusicApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            MusicApplicationEntryPoint::class.java,
        )
        val imageHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                AuthenticatedApiMediaInterceptor(
                    authSessionManager = entryPoint.authSessionManager(),
                    serverInfoRepository = entryPoint.serverInfoRepository(),
                )
            )
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
        fun authSessionManager(): AuthSessionManager
        fun serverInfoRepository(): ServerInfoRepository
    }
}

package sstu.grivvus.ym.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import sstu.grivvus.ym.data.network.auth.AuthenticatedMediaInterceptor
import sstu.grivvus.ym.data.network.remote.stream.OkHttpStreamingRemoteDataSource
import sstu.grivvus.ym.data.network.remote.stream.StreamingRemoteDataSource
import sstu.grivvus.ym.playback.artwork.FilePlaybackArtworkCache
import sstu.grivvus.ym.playback.artwork.PlaybackArtworkCache
import sstu.grivvus.ym.playback.controller.MediaSessionPlaybackController
import sstu.grivvus.ym.playback.controller.PlaybackController
import sstu.grivvus.ym.playback.queue.DefaultPlaybackQueueFactory
import sstu.grivvus.ym.playback.queue.PlaybackQueueFactory

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PlaybackHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        implementation: MediaSessionPlaybackController,
    ): PlaybackController

    @Binds
    @Singleton
    abstract fun bindPlaybackQueueFactory(
        implementation: DefaultPlaybackQueueFactory,
    ): PlaybackQueueFactory

    @Binds
    @Singleton
    abstract fun bindStreamingRemoteDataSource(
        implementation: OkHttpStreamingRemoteDataSource,
    ): StreamingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindPlaybackArtworkCache(
        implementation: FilePlaybackArtworkCache,
    ): PlaybackArtworkCache
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackNetworkModule {
    @Provides
    @Singleton
    @PlaybackHttpClient
    fun providePlaybackOkHttpClient(
        authenticatedMediaInterceptor: AuthenticatedMediaInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authenticatedMediaInterceptor)
            .build()
    }
}

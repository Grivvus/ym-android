package sstu.grivvus.ym.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import sstu.grivvus.ym.data.network.auth.AuthHeaderProvider
import sstu.grivvus.ym.data.network.auth.AuthenticatedMediaInterceptor
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.auth.DefaultAuthHeaderProvider
import sstu.grivvus.ym.data.network.auth.DefaultAuthSessionManager
import sstu.grivvus.ym.data.network.auth.TokenRefresher
import sstu.grivvus.ym.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.DefaultApiExecutor
import sstu.grivvus.ym.data.network.core.DefaultErrorBodyParser
import sstu.grivvus.ym.data.network.core.ErrorBodyParser
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.core.NetworkLogger
import sstu.grivvus.ym.data.network.core.OpenApiGeneratedApiProvider
import sstu.grivvus.ym.data.network.core.ServerInfoApiBaseUrlProvider
import sstu.grivvus.ym.data.network.core.TimberNetworkLogger
import sstu.grivvus.ym.data.network.remote.album.AlbumRemoteDataSource
import sstu.grivvus.ym.data.network.remote.album.OpenApiAlbumRemoteDataSource
import sstu.grivvus.ym.data.network.remote.artist.ArtistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.artist.OpenApiArtistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.auth.AuthRemoteDataSource
import sstu.grivvus.ym.data.network.remote.auth.OpenApiAuthRemoteDataSource
import sstu.grivvus.ym.data.network.remote.playlist.OpenApiPlaylistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.ym.data.network.remote.server.OpenApiServerProbeRemoteDataSource
import sstu.grivvus.ym.data.network.remote.server.ServerProbeRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.OpenApiTrackRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.OkHttpTrackDownloadRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.TrackDownloadRemoteDataSource
import sstu.grivvus.ym.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.ym.data.network.remote.user.OpenApiUserRemoteDataSource
import sstu.grivvus.ym.data.network.remote.user.UserRemoteDataSource
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val TRACK_UPLOAD_READ_TIMEOUT_MINUTES = 5L
private const val TRACK_UPLOAD_WRITE_TIMEOUT_MINUTES = 5L
private const val ARCHIVE_TRANSFER_READ_TIMEOUT_MINUTES = 5L
private const val ARCHIVE_TRANSFER_WRITE_TIMEOUT_MINUTES = 5L

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TrackUploadHttpClient

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ArchiveTransferHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModules {
    @Binds
    @Singleton
    abstract fun bindApiBaseUrlProvider(
        implementation: ServerInfoApiBaseUrlProvider,
    ): ApiBaseUrlProvider

    @Binds
    @Singleton
    abstract fun bindGeneratedApiProvider(
        implementation: OpenApiGeneratedApiProvider,
    ): GeneratedApiProvider

    @Binds
    @Singleton
    abstract fun bindApiExecutor(
        implementation: DefaultApiExecutor,
    ): ApiExecutor

    @Binds
    @Singleton
    abstract fun bindErrorBodyParser(
        implementation: DefaultErrorBodyParser,
    ): ErrorBodyParser

    @Binds
    @Singleton
    abstract fun bindNetworkLogger(
        implementation: TimberNetworkLogger,
    ): NetworkLogger

    @Binds
    @Singleton
    abstract fun bindAuthSessionManager(
        implementation: DefaultAuthSessionManager,
    ): AuthSessionManager

    @Binds
    @Singleton
    abstract fun bindAuthHeaderProvider(
        implementation: DefaultAuthHeaderProvider,
    ): AuthHeaderProvider

    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        implementation: OpenApiAuthRemoteDataSource,
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTokenRefresher(
        implementation: OpenApiAuthRemoteDataSource,
    ): TokenRefresher

    @Binds
    @Singleton
    abstract fun bindUserRemoteDataSource(
        implementation: OpenApiUserRemoteDataSource,
    ): UserRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindArtistRemoteDataSource(
        implementation: OpenApiArtistRemoteDataSource,
    ): ArtistRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAlbumRemoteDataSource(
        implementation: OpenApiAlbumRemoteDataSource,
    ): AlbumRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindPlaylistRemoteDataSource(
        implementation: OpenApiPlaylistRemoteDataSource,
    ): PlaylistRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTrackRemoteDataSource(
        implementation: OpenApiTrackRemoteDataSource,
    ): TrackRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTrackDownloadRemoteDataSource(
        implementation: OkHttpTrackDownloadRemoteDataSource,
    ): TrackDownloadRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindServerProbeRemoteDataSource(
        implementation: OpenApiServerProbeRemoteDataSource,
    ): ServerProbeRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkClientModule {
    @Provides
    @Singleton
    @TrackUploadHttpClient
    fun provideTrackUploadHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .readTimeout(TRACK_UPLOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(TRACK_UPLOAD_WRITE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    @ArchiveTransferHttpClient
    fun provideArchiveTransferHttpClient(
        authenticatedMediaInterceptor: AuthenticatedMediaInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authenticatedMediaInterceptor)
            .readTimeout(ARCHIVE_TRANSFER_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(ARCHIVE_TRANSFER_WRITE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }
}

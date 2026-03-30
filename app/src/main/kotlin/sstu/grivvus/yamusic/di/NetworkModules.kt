package sstu.grivvus.yamusic.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.auth.AuthHeaderProvider
import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.data.network.auth.DefaultAuthHeaderProvider
import sstu.grivvus.yamusic.data.network.auth.DefaultAuthSessionManager
import sstu.grivvus.yamusic.data.network.auth.TokenRefresher
import sstu.grivvus.yamusic.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.DefaultApiExecutor
import sstu.grivvus.yamusic.data.network.core.DefaultErrorBodyParser
import sstu.grivvus.yamusic.data.network.core.ErrorBodyParser
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.core.NetworkLogger
import sstu.grivvus.yamusic.data.network.core.OpenApiGeneratedApiProvider
import sstu.grivvus.yamusic.data.network.core.ServerInfoApiBaseUrlProvider
import sstu.grivvus.yamusic.data.network.core.TimberNetworkLogger
import sstu.grivvus.yamusic.data.network.remote.auth.AuthRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.auth.OpenApiAuthRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.playlist.OpenApiPlaylistRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.playlist.PlaylistRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.server.OpenApiServerProbeRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.server.ServerProbeRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.track.OpenApiTrackRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.track.TrackRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.user.OpenApiUserRemoteDataSource
import sstu.grivvus.yamusic.data.network.remote.user.UserRemoteDataSource

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
    abstract fun bindServerProbeRemoteDataSource(
        implementation: OpenApiServerProbeRemoteDataSource,
    ): ServerProbeRemoteDataSource
}

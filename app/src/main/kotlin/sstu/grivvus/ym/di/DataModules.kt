package sstu.grivvus.ym.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import sstu.grivvus.ym.data.local.AlbumDao
import sstu.grivvus.ym.data.local.AppDatabase
import sstu.grivvus.ym.data.local.ArtistDao
import sstu.grivvus.ym.data.local.AudioTrackDao
import sstu.grivvus.ym.data.local.PlaylistDao
import sstu.grivvus.ym.data.local.PlaylistTrackDao
import sstu.grivvus.ym.data.local.ServerInfoDao
import sstu.grivvus.ym.data.local.TrackAlbumDao
import sstu.grivvus.ym.data.local.UserDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "yamusic.db"

    @Singleton
    @Provides
    fun provideDataBase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideAudioTrackDao(database: AppDatabase): AudioTrackDao = database.audioTrackDao()

    @Provides
    fun provideArtistDao(database: AppDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideAlbumDao(database: AppDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideTrackAlbumDao(database: AppDatabase): TrackAlbumDao = database.trackAlbumDao()

    @Provides
    fun provideServerInfoDao(database: AppDatabase): ServerInfoDao = database.serverInfoDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlaylistTrackDao(database: AppDatabase): PlaylistTrackDao =
        database.playlistTrackDao()
}

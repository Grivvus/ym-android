package sstu.grivvus.yamusic.di

import android.content.Context
import androidx.room.Room
import sstu.grivvus.yamusic.data.User
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.AppDatabase
import sstu.grivvus.yamusic.data.local.UserDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import sstu.grivvus.yamusic.data.local.DatabaseProvider
import sstu.grivvus.yamusic.data.local.LibraryTrackDao
import sstu.grivvus.yamusic.data.local.PlaylistDao
import sstu.grivvus.yamusic.data.local.PlaylistTrackDao
import sstu.grivvus.yamusic.data.local.ServerInfoDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDataBase(@ApplicationContext context: Context): AppDatabase {
        return DatabaseProvider.getDB(context)
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideAudioTrackDao(database: AppDatabase): AudioTrackDao = database.audioTrackDao()

    @Provides
    fun provideServerInfoDao(database: AppDatabase): ServerInfoDao = database.serverInfoDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideLibraryTrackDao(database: AppDatabase): LibraryTrackDao = database.libraryTrackDao()

    @Provides
    fun providePlaylistTrackDao(database: AppDatabase): PlaylistTrackDao = database.playlistTrackDao()
}

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
import sstu.grivvus.yamusic.data.local.DatabaseProvider
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
}
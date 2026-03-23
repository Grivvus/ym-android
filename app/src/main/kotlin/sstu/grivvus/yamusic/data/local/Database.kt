package sstu.grivvus.yamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LocalUser::class, AudioTrack::class, ServerInfo::class, Artist::class,
        Album::class, Playlist::class, LibraryTrack::class, PlaylistTrackCrossRef::class,
    ],
    version = 3
)
@TypeConverters(UriConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun serverInfoDao(): ServerInfoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun libraryTrackDao(): LibraryTrackDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
}

object DatabaseProvider {
    var instance: AppDatabase? = null

    fun initDB(context: Context) {
        instance = Room
            .databaseBuilder(context, AppDatabase::class.java, "database")
            .fallbackToDestructiveMigration(true)
            .build()
    }

    fun getDB(context: Context): AppDatabase {
        if (instance == null) {
            initDB(context)
        }
        return instance!!
    }

    fun deleteDB(context: Context) {
        instance?.close()
        instance = null
        context.deleteDatabase("database")
    }

}

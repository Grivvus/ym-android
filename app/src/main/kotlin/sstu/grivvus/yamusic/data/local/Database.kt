package sstu.grivvus.yamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LocalUser::class, AudioTrack::class, ServerInfo::class, Artist::class,
        Album::class, Playlist::class,
    ],
    version = 1
)
@TypeConverters(UriConverter::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun audioTrackDao(): AudioTrackDao
}

object DatabaseProvider {
    var instance: AppDatabase? = null

    fun initDB(context: Context) {
        instance = Room
            .databaseBuilder(context, AppDatabase::class.java, "database")
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
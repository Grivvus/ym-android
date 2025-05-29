package sstu.grivvus.yamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.hilt.android.qualifiers.ApplicationContext

@Database(entities = [LocalUser::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun userDao(): UserDao
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
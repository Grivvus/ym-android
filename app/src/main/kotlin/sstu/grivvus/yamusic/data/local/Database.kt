package sstu.grivvus.yamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocalUser::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun userDao(): UserDao
}

object DatabaseProvider {
    lateinit var instance: AppDatabase

    fun initDB(context: Context) {
        instance = Room.databaseBuilder(context, AppDatabase::class.java, "database").build()
    }

    fun getDB(): AppDatabase {
        return instance
    }

}
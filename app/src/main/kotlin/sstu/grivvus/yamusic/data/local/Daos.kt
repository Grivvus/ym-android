package sstu.grivvus.yamusic.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: LocalUser)

    @Upsert
    suspend fun upsert(user: LocalUser)

    @Query("UPDATE user SET token = :newToken WHERE username=:username")
    suspend fun updateToken(username: String, newToken: String)

    @Update
    suspend fun update(user: LocalUser)

    @Delete
    suspend fun delete(user: LocalUser)

    @Query("delete from user")
    suspend fun clearTable()

    @Query("select * from user limit 1")
    suspend fun getActiveUser(): LocalUser

    @Query("select token from user where username=:username limit 1")
    suspend fun getUserToken(username: String): String
}
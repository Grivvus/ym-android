package sstu.grivvus.yamusic.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert
    fun insert(user: LocalUser)

    @Query("UPDATE user SET token = :newToken WHERE username=:username")
    fun updateToken(username: String, newToken: String)

    @Update
    fun update(user: LocalUser)

    @Delete
    fun delete(user: LocalUser)

    @Query("select * from user where username=:username limit 1")
    fun getUser(username: String)

    @Query("select token from user where username=:username limit 1")
    fun getUserToken(username: String)
}
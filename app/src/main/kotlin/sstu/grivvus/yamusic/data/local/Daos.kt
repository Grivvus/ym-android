package sstu.grivvus.yamusic.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

@Dao
interface AudioTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: AudioTrack)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<AudioTrack>)

    @Update
    suspend fun update(track: AudioTrack)

    @Delete
    suspend fun delete(track: AudioTrack)

    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    suspend fun getAllTracks(): List<AudioTrack>

//    @Query("SELECT * FROM audio_tracks WHERE is_favorite = 1 ORDER BY title ASC")
//    suspend fun getFavoriteTracks(): List<AudioTrack>

    @Query("SELECT * FROM audio_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): AudioTrack?

//    @Query("SELECT * FROM audio_tracks WHERE media_store_id = :mediaStoreId")
//    suspend fun getTrackByMediaStoreId(mediaStoreId: Long): AudioTrack?

//    @Query("UPDATE audio_tracks SET is_favorite = :isFavorite WHERE id = :trackId")
//    suspend fun setFavorite(trackId: Long, isFavorite: Boolean)
//
//    @Query("UPDATE audio_tracks SET play_count = play_count + 1, last_played = :timestamp WHERE id = :trackId")
//    suspend fun incrementPlayCount(trackId: Long, timestamp: Long)

    @Query("DELETE FROM audio_tracks")
    suspend fun clearAll()
}
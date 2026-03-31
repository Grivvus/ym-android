package sstu.grivvus.yamusic.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface ServerInfoDao {
    @Upsert
    suspend fun insertOrUpdate(info: ServerInfo)

    @Query("select * from server_info order by rowid desc limit 1")
    suspend fun get(): ServerInfo?

    @Query("delete from server_info")
    suspend fun clear()
}

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: LocalUser)

    @Upsert
    suspend fun upsert(user: LocalUser)

    @Query(
        "UPDATE user SET access_token = :newAccess, refresh_token = :newRefresh WHERE remote_id = :id"
    )
    suspend fun updateTokens(id: Long, newAccess: String, newRefresh: String)

    @Update
    suspend fun update(user: LocalUser)

    @Delete
    suspend fun delete(user: LocalUser)

    @Query("delete from user")
    suspend fun clearTable()

    @Query("select * from user limit 1")
    suspend fun getActiveUser(): LocalUser?

    @Query("select access_token, refresh_token from user where remote_id=:id limit 1")
    suspend fun getUserTokens(id: Long): Tokens
}

@Dao
interface AudioTrackDao {
    @Upsert
    suspend fun upsert(track: AudioTrack)

    @Upsert
    suspend fun upsertAll(tracks: List<AudioTrack>)

    @Update
    suspend fun update(track: AudioTrack)

    @Delete
    suspend fun delete(track: AudioTrack)

    @Query("SELECT * FROM audio_tracks ORDER BY name ASC")
    suspend fun getAll(): List<AudioTrack>

//    @Query("SELECT * FROM audio_tracks WHERE is_favorite = 1 ORDER BY title ASC")
//    suspend fun getFavoriteTracks(): List<AudioTrack>

    @Query("SELECT * FROM audio_tracks WHERE remote_id = :trackId LIMIT 1")
    suspend fun getById(trackId: Long): AudioTrack?

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

@Dao
interface TrackAlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: TrackAlbumCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(refs: List<TrackAlbumCrossRef>)

    @Query("SELECT * FROM track_album_cross_ref")
    suspend fun getAll(): List<TrackAlbumCrossRef>

    @Query("SELECT album_id FROM track_album_cross_ref WHERE track_id = :trackId ORDER BY album_id ASC")
    suspend fun getAlbumIdsForTrack(trackId: Long): List<Long>

    @Query("DELETE FROM track_album_cross_ref WHERE track_id = :trackId")
    suspend fun deleteForTrack(trackId: Long)

    @Query("DELETE FROM track_album_cross_ref")
    suspend fun clearAll()
}

@Dao
interface ArtistDao {
    @Upsert
    suspend fun upsert(artist: Artist)

    @Upsert
    suspend fun upsertAll(artists: List<Artist>)

    @Query("SELECT * FROM artist WHERE remote_id = :artistId LIMIT 1")
    suspend fun getById(artistId: Long): Artist?
}

@Dao
interface AlbumDao {
    @Upsert
    suspend fun upsert(album: Album)

    @Upsert
    suspend fun upsertAll(albums: List<Album>)

    @Query("SELECT * FROM album ORDER BY name ASC")
    suspend fun getAll(): List<Album>

    @Query("SELECT * FROM album WHERE remote_id = :albumId LIMIT 1")
    suspend fun getById(albumId: Long): Album?
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsert(playlist: Playlist)

    @Upsert
    suspend fun upsertAll(playlists: List<Playlist>)

    @Query("SELECT * FROM playlist ORDER BY name ASC")
    suspend fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlist WHERE remote_id = :playlistId LIMIT 1")
    suspend fun getById(playlistId: Long): Playlist?

    @Query("SELECT * FROM playlist WHERE owner_remote_id = :ownerRemoteId AND name = :name LIMIT 1")
    suspend fun getByUserAndName(ownerRemoteId: Long, name: String): Playlist?

    @Query("DELETE FROM playlist WHERE remote_id = :playlistId")
    suspend fun deleteById(playlistId: Long)
}

@Dao
interface PlaylistTrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<PlaylistTrackCrossRef>)

    @Query("SELECT track_id FROM playlist_track_cross_ref WHERE playlist_id = :playlistId")
    suspend fun getTrackIdsForPlaylist(playlistId: Long): List<Long>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlist_id = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)
}

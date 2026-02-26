package sstu.grivvus.yamusic.data.local

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "server_info", primaryKeys = ["ip", "port"])
data class ServerInfo(
    @ColumnInfo() val ip: String,
    @ColumnInfo() val port: String,
)

@Entity(
    tableName = "user",
    primaryKeys = ["remote_id"],
)
@TypeConverters(UriConverter::class)
data class LocalUser(
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String?,
    @ColumnInfo(name = "access_token") val access: String?,
    @ColumnInfo(name = "refresh_token") val refresh: String?,
    @ColumnInfo(name = "avatar_uri") val avatarUri: Uri? = null,
)

data class Tokens(
    @ColumnInfo("access_token") val access: String,
    @ColumnInfo("refresh_token") val refresh: String,
)

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(Artist::class, ["id"], ["artist_id"]),
        ForeignKey(Album::class, ["id"], ["album_id"])
    ],
)
@TypeConverters(UriConverter::class)
data class AudioTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "server_id") val servId: Long,
    @ColumnInfo("artist_id") val artistId: Long,
    @ColumnInfo("album_id") val albumId: Long,
    @ColumnInfo() val name: String,
    @ColumnInfo() val duration: Long,
    @ColumnInfo() val uri: Uri,
    @ColumnInfo val localPath: String? = null,
    @ColumnInfo val isDownloaded: Boolean = false
)

@Entity(tableName = "artist")
@TypeConverters(UriConverter::class)
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo() val name: String,
    @ColumnInfo(name = "image_uri") val imageUri: Uri? = null,
)

@Entity(
    tableName = "album",
    foreignKeys = [
        ForeignKey(Artist::class, ["id"], ["artist_id"])
    ]
)
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo() val name: String,
)

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo() val name: String,
)
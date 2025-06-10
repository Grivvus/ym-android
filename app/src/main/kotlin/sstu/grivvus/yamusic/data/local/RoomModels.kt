package sstu.grivvus.yamusic.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import android.net.Uri
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "user",
    primaryKeys = ["server_id"],
    )
data class LocalUser(
    @ColumnInfo(name = "server_id") val servId: Long,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String?,
    @ColumnInfo(name = "token") val token: String?,
    @ColumnInfo(name = "avatar_uri") val avatarUri: String? = null,
)

@Entity(tableName = "audio_tracks")
@TypeConverters(UriConverter::class)
data class AudioTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name="server_id") val servId: Long,
    @ColumnInfo() val title: String,
//    @ColumnInfo() val duration: Long,
    @ColumnInfo() val artist: String,
    @ColumnInfo() val album: String,
    @ColumnInfo() val uri: Uri,
    @ColumnInfo val localPath: String? = null,
    @ColumnInfo val isDownloaded: Boolean = false
//    @ColumnInfo(name = "album_art_uri") val albumArtUri: Uri?,
)
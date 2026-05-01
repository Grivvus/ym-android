package sstu.grivvus.ym.data.local

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import sstu.grivvus.ym.data.PlaylistType
import java.time.LocalDate

@Entity(tableName = "server_info", primaryKeys = ["host", "port"])
data class ServerInfo(
    @ColumnInfo() val host: String,
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
    @ColumnInfo(name = "is_superuser", defaultValue = "0") val isSuperuser: Boolean = false,
    @ColumnInfo(name = "avatar_uri") val avatarUri: Uri? = null,
)

data class Tokens(
    @ColumnInfo("access_token") val access: String,
    @ColumnInfo("refresh_token") val refresh: String,
)

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(Artist::class, ["remote_id"], ["artist_id"]),
    ],
    indices = [Index("artist_id")],
)
@TypeConverters(UriConverter::class)
data class AudioTrack(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Long,
    @ColumnInfo("artist_id") val artistId: Long,
    @ColumnInfo() val name: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "uri_fast") val uriFast: Uri? = null,
    @ColumnInfo(name = "uri_standard") val uriStandard: Uri? = null,
    @ColumnInfo(name = "uri_high") val uriHigh: Uri? = null,
    @ColumnInfo(name = "uri_lossless") val uriLossless: Uri? = null,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "is_downloaded") val isDownloaded: Boolean = false,
)

@Entity(tableName = "artist")
@TypeConverters(UriConverter::class)
data class Artist(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Long,
    @ColumnInfo() val name: String,
    @ColumnInfo(name = "image_uri") val imageUri: Uri? = null,
)

@Entity(
    tableName = "album",
    foreignKeys = [
        ForeignKey(Artist::class, ["remote_id"], ["artist_id"])
    ],
    indices = [Index("artist_id")],
)
@TypeConverters(
    UriConverter::class,
    DateConverter::class,
)
data class Album(
    @PrimaryKey
    @ColumnInfo(name = "remote_id")
    val remoteId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo() val name: String = "",
    @ColumnInfo(name = "cover_uri") val coverUri: Uri? = null,
    @ColumnInfo(name = "release_year") val releaseYear: Int? = null,
    @ColumnInfo(name = "release_full_date") val releaseDate: LocalDate? = null,
)

@Entity(tableName = "playlist")
@TypeConverters(
    UriConverter::class,
    PlaylistTypeConverter::class,
)
data class Playlist(
    @PrimaryKey
    @ColumnInfo(name = "remote_id") val remoteId: Long,
    @ColumnInfo(name = "owner_remote_id") val ownerRemoteId: Long,
    @ColumnInfo() val name: String,
    @ColumnInfo(name = "cover_uri") val coverUri: Uri? = null,
    @ColumnInfo(name = "playlist_type", defaultValue = "'owned'")
    val playlistType: PlaylistType = PlaylistType.OWNED,
    @ColumnInfo(name = "can_edit", defaultValue = "1")
    val canEdit: Boolean = true,
    @ColumnInfo(name = "name_is_local_override") val nameIsLocalOverride: Boolean = false,
    @ColumnInfo(name = "tracks_seeded") val tracksSeeded: Boolean = false,
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(Playlist::class, ["remote_id"], ["playlist_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AudioTrack::class, ["remote_id"], ["track_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("track_id")],
)
data class PlaylistTrackCrossRef(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
)

@Entity(
    tableName = "track_album_cross_ref",
    primaryKeys = ["track_id", "album_id"],
    foreignKeys = [
        ForeignKey(AudioTrack::class, ["remote_id"], ["track_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Album::class, ["remote_id"], ["album_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("album_id")],
)
data class TrackAlbumCrossRef(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long,
)

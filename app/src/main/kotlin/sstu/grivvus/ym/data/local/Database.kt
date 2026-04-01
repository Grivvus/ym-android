package sstu.grivvus.ym.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LocalUser::class, AudioTrack::class, ServerInfo::class, Artist::class,
        Album::class, Playlist::class, PlaylistTrackCrossRef::class, TrackAlbumCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UriConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackAlbumDao(): TrackAlbumDao
    abstract fun serverInfoDao(): ServerInfoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
}

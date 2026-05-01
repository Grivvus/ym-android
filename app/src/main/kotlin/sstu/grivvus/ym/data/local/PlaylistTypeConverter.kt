package sstu.grivvus.ym.data.local

import androidx.room.TypeConverter
import sstu.grivvus.ym.data.PlaylistType

class PlaylistTypeConverter {
    @TypeConverter
    fun fromPlaylistType(type: PlaylistType): String = type.name.lowercase()

    @TypeConverter
    fun toPlaylistType(value: String): PlaylistType {
        return when (value.lowercase()) {
            "owned" -> PlaylistType.OWNED
            "public" -> PlaylistType.PUBLIC
            "shared" -> PlaylistType.SHARED
            else -> PlaylistType.OWNED
        }
    }
}

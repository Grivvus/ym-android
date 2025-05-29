package sstu.grivvus.yamusic.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "user",
    primaryKeys = ["username"],
    )
data class LocalUser(
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String?,
    @ColumnInfo(name = "token") val token: String?,
)
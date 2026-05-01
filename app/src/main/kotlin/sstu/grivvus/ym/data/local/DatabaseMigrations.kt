package sstu.grivvus.ym.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE playlist ADD COLUMN playlist_type TEXT NOT NULL DEFAULT 'owned'",
        )
        db.execSQL(
            "ALTER TABLE playlist ADD COLUMN can_edit INTEGER NOT NULL DEFAULT 1",
        )
    }
}

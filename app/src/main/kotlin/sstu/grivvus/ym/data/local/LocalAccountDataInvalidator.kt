package sstu.grivvus.ym.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.di.IoDispatcher

@Singleton
class LocalAccountDataInvalidator @Inject constructor(
    private val database: AppDatabase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun invalidate() {
        withContext(ioDispatcher) {
            database.withTransaction {
                database.userDao().clearTable()
                database.playlistTrackDao().clearAll()
                database.trackAlbumDao().clearAll()
                database.audioTrackDao().clearAll()
                database.albumDao().clearAll()
                database.artistDao().clearAll()
                database.playlistDao().clearAll()
            }
        }
    }
}

package sstu.grivvus.ym.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.di.IoDispatcher

@Singleton
class PlaybackPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _preferredTrackQuality = MutableStateFlow(readPreferredTrackQuality())

    val preferredTrackQuality: StateFlow<TrackQuality> = _preferredTrackQuality.asStateFlow()

    fun currentPreferredTrackQuality(): TrackQuality = _preferredTrackQuality.value

    suspend fun savePreferredTrackQuality(quality: TrackQuality) {
        withContext(ioDispatcher) {
            sharedPreferences.edit()
                .putString(KEY_PREFERRED_TRACK_QUALITY, quality.name)
                .apply()
            _preferredTrackQuality.value = quality
        }
    }

    private fun readPreferredTrackQuality(): TrackQuality {
        val storedValue = sharedPreferences.getString(KEY_PREFERRED_TRACK_QUALITY, null)
        return storedValue
            ?.let { value -> TrackQuality.entries.firstOrNull { it.name == value } }
            ?: TrackQuality.STANDARD
    }

    private companion object {
        private const val PREFERENCES_NAME = "playback_preferences"
        private const val KEY_PREFERRED_TRACK_QUALITY = "preferred_track_quality"
    }
}

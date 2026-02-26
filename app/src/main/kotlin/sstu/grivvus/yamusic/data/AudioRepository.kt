package sstu.grivvus.yamusic.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import sstu.grivvus.yamusic.data.local.AudioTrackDao
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.di.DefaultDispatcher
import javax.inject.Inject

class AudioRepository @Inject constructor(
    val dao: AudioTrackDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context
) {
    private val contentResolver = context.contentResolver

    init {
        Log.i("AudioRepository", "audio repository INIT")
    }

}
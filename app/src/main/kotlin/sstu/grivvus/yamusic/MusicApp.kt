package sstu.grivvus.yamusic

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initCoroutineExceptionHandler()
    }

    private fun initCoroutineExceptionHandler() {
        CoroutineScope(Dispatchers.IO).launch {
            CoroutineExceptionHandler { _, _ -> }
        }
    }
}
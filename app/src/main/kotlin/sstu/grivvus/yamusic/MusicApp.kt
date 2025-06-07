package sstu.grivvus.yamusic

import android.app.Application
import android.os.StrictMode
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@HiltAndroidApp
class MusicApplication : Application() {
    override fun onCreate() {
//        StrictMode.setThreadPolicy(
//            StrictMode.ThreadPolicy.Builder()
//                .detectAll()
//                .penaltyLog()
//                .build()
//        )
//
//        StrictMode.setVmPolicy(
//            StrictMode.VmPolicy.Builder()
//                .detectLeakedSqlLiteObjects()
//                .detectLeakedClosableObjects()
//                .detectActivityLeaks()
//                .penaltyLog()
//                .build()
//        )
        super.onCreate()
        initCoroutineExceptionHandler()
    }

    private fun initCoroutineExceptionHandler() {
        CoroutineScope(Dispatchers.IO).launch {
            CoroutineExceptionHandler { _, _ -> }
        }
    }
}
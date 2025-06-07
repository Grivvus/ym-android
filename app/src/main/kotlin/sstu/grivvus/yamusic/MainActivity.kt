package sstu.grivvus.yamusic

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import sstu.grivvus.yamusic.data.local.DatabaseProvider
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("APP_CRASH", "Uncaught exception in thread: ${thread.name}", throwable)
            finishAffinity()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YaMusicTheme {
                YaMusicNavGraph()
            }
        }
    }
}
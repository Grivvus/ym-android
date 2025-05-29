package sstu.grivvus.yamusic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sstu.grivvus.yamusic.NavigationActions
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme

@Composable
fun BlankScreen(actions: NavigationActions) {
    YaMusicTheme {
        Scaffold(
            bottomBar = { BottomBar(
                { actions.navigateToMusic() },
                { actions.navigateToLibrary() },
                { actions.navigateToProfile() },
            ) }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.DarkGray)
            )
        }
    }
}
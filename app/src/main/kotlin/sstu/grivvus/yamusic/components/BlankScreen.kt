package sstu.grivvus.yamusic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme

@Composable
fun BlankScreen(
    title: String,
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
) {
    YaMusicTheme {
        Scaffold(
            bottomBar = {
                BottomBar(
                    onMusicClick = navigateToMusic,
                    onLibraryClick = navigateToLibrary,
                    onProfileClick = navigateToProfile,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(title)
            }
        }
    }
}

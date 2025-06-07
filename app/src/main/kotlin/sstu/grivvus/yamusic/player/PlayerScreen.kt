package sstu.grivvus.yamusic.player
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.sharp.Pause
import androidx.compose.material.icons.sharp.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import sstu.grivvus.yamusic.components.BottomBar
import sstu.grivvus.yamusic.data.local.AudioTrack
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons

//@androidx.annotation.OptIn(UnstableApi::class)
//@Composable
//fun AudioPlayerScreen(
//    navigateToMusic: () -> Unit,
//    navigateToLibrary:  () -> Unit,
//    navigateToProfile: () -> Unit,
//    audioUri: String,
//) {
//    val context = LocalContext.current
//    val viewModel = PlayerViewModel(context)
//    val playerState by viewModel.uiState.collectAsStateWithLifecycle()
//
//    YaMusicTheme {
//        Scaffold(
////            bottomBar = {
////                BottomBar(
////                    navigateToMusic, navigateToLibrary, navigateToProfile
////                )
////            }
//        ) { padding ->
//
//            LaunchedEffect(Unit) {
//                viewModel.loadAudio(audioUri)
//            }
//
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(16.dp),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
//            ) {
//                PlayerViewContainer(viewModel.player)
//
//                Spacer(modifier = Modifier.height(24.dp))
//
//                PlayerControls(
//                    playerState = playerState,
//                    onPlayPause = { viewModel.playPause() },
//                    onSeek = { viewModel.seekTo(it) }
//                )
//            }
//        }
//    }
//}
//
//@androidx.annotation.OptIn(UnstableApi::class)
//@Composable
//fun PlayerViewContainer(player: ExoPlayer) {
//    AndroidView(
//        factory = { context ->
//            PlayerView(context).apply {
//                this.player = player
//                useController = false
//            }
//        },
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(56.dp)
//    )
//}
//
//@Composable
//fun PlayerControls(
//    playerState: PlayerState,
//    onPlayPause: () -> Unit,
//    onSeek: (Long) -> Unit
//) {
//    when (playerState) {
//        is PlayerState.Ready -> {
//            val position = playerState.position
//            val duration = playerState.duration
//
//            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                Slider(
//                    value = position.toFloat(),
//                    onValueChange = { onSeek(it.toLong()) },
//                    valueRange = 0f..duration.toFloat(),
//                    modifier = Modifier.fillMaxWidth()
//                )
//
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(16.dp)
//                ) {
//                    Text(text = formatTime(position))
//                    IconButton(onClick = onPlayPause) {
//                        Icon(
//                            imageVector = if (playerState.isPlaying) appIcons.Pause
//                            else appIcons.PlayArrow,
//                            contentDescription = "play button"
//                        )
//                    }
//                    Text(text = formatTime(duration))
//                }
//            }
//        }
//        is PlayerState.Buffering -> CircularProgressIndicator()
//        is PlayerState.Ended -> Button(onClick = { onSeek(0) }) {
//            Text("Restart")
//        }
//        else -> {}
//    }
//}
//
//fun formatTime(millis: Long): String {
//    val seconds = millis / 1000
//    val minutes = seconds / 60
//    val remainingSeconds = seconds % 60
//    return String.format("%02d:%02d", minutes, remainingSeconds)
//}
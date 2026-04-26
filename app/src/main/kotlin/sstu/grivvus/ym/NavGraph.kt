package sstu.grivvus.ym

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sstu.grivvus.ym.album.AlbumScreen
import sstu.grivvus.ym.artist.ArtistScreen
import sstu.grivvus.ym.data.network.auth.SessionState
import sstu.grivvus.ym.library.LibraryScreen
import sstu.grivvus.ym.login.LoginScreen
import sstu.grivvus.ym.music.MusicScreen
import sstu.grivvus.ym.music.UploadScreen
import sstu.grivvus.ym.passwordReset.PasswordResetScreen
import sstu.grivvus.ym.playback.MiniPlayerOverlay
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.playback.PlayerScreen
import sstu.grivvus.ym.playlist.PlaylistScreen
import sstu.grivvus.ym.profile.ProfileScreen
import sstu.grivvus.ym.register.RegistrationScreen
import sstu.grivvus.ym.serverSetup.ServerSetup
import sstu.grivvus.ym.startup.StartupScreen

internal fun appProtectedRoutes(): Set<String> = setOf(
    AppDestinations.MUSIC_ROUTE,
    AppDestinations.ARTIST_ROUTE,
    AppDestinations.ALBUM_ROUTE,
    AppDestinations.PLAYLIST_ROUTE,
    AppDestinations.PROFILE_ROUTE,
    AppDestinations.LIBRARY_ROUTE,
    AppDestinations.UPLOAD_ROUTE,
    AppDestinations.PLAYER_ROUTE,
)

internal fun shouldRedirectToLogin(
    sessionState: SessionState,
    currentRoute: String?,
    protectedRoutes: Set<String> = appProtectedRoutes(),
): Boolean {
    return sessionState is SessionState.Unauthenticated && currentRoute in protectedRoutes
}

@Composable
fun YMNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestinations.STARTUP_ROUTE,
    navActions: NavigationActions = remember(navController) {
        NavigationActions(navController)
    }
) {
    val sessionViewModel: AppSessionViewModel = hiltViewModel()
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val playbackState by playbackViewModel.playbackState.collectAsStateWithLifecycle()
    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStackEntry?.destination?.route ?: startDestination
    val protectedRoutes = remember { appProtectedRoutes() }
    val showMiniPlayer = playbackState.currentTrack != null &&
            currentRoute != AppDestinations.PLAYER_ROUTE &&
            currentRoute in protectedRoutes

    LaunchedEffect(sessionState, currentRoute, navController) {
        if (shouldRedirectToLogin(sessionState, currentRoute, protectedRoutes)) {
            navActions.navigateToLoginClearingBackStack()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(AppDestinations.STARTUP_ROUTE) {
                StartupScreen(
                    onRouteResolved = { route ->
                        navController.navigate(route) {
                            popUpTo(AppDestinations.STARTUP_ROUTE) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestinations.SERVER_SETUP_ROUTE) {
                ServerSetup(
                    onProceed = {
                        navController.navigate(AppDestinations.REGISTRATION_ROUTE) {
                            popUpTo(AppDestinations.SERVER_SETUP_ROUTE) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestinations.LOGIN_ROUTE) {
                LoginScreen(
                    onSignUpClick = { navActions.navigateToRegistration() },
                    onPasswordResetClick = { navActions.navigateToPasswordReset() },
                    onSuccess = { navActions.navigateToMusicFromAuth() },
                )
            }
            composable(AppDestinations.REGISTRATION_ROUTE) {
                RegistrationScreen(
                    onSignInClick = { navActions.navigateToLogin() },
                    onPasswordResetClick = { navActions.navigateToPasswordReset() },
                    onSuccess = { navActions.navigateToMusicFromAuth() },
                )
            }
            composable(AppDestinations.PASSWORD_RESET_ROUTE) {
                PasswordResetScreen(
                    onBackToSignIn = { navActions.navigateToLogin() },
                )
            }
            composable(AppDestinations.PROFILE_ROUTE) {
                ProfileScreen(
                    { navActions.navigateToMusic() },
                    { navActions.navigateToLibrary() },
                    { navActions.navigateToProfile() },
                )
            }

            composable(AppDestinations.LIBRARY_ROUTE) {
                LibraryScreen(
                    navigateToMusic = { navActions.navigateToMusic() },
                    navigateToLibrary = { navActions.navigateToLibrary() },
                    navigateToProfile = { navActions.navigateToProfile() },
                    navigateToArtist = { artistId -> navActions.navigateToArtist(artistId) },
                    navigateToAlbum = { albumId -> navActions.navigateToAlbum(albumId) },
                )
            }

            composable(AppDestinations.MUSIC_ROUTE) { backStackEntry ->
                val refreshToken by backStackEntry.savedStateHandle
                    .getStateFlow(RouteArguments.PLAYLIST_LIST_REFRESH_TOKEN, 0L)
                    .collectAsStateWithLifecycle()
                MusicScreen(
                    { navActions.navigateToMusic() },
                    { navActions.navigateToLibrary() },
                    { navActions.navigateToProfile() },
                    { playlistId -> navActions.navigateToPlaylist(playlistId) },
                    refreshToken = refreshToken,
                )
            }

            composable(
                route = AppDestinations.ARTIST_ROUTE,
                arguments = listOf(
                    navArgument(RouteArguments.ARTIST_ID) {
                        type = NavType.LongType
                    },
                ),
            ) {
                ArtistScreen(
                    navigateToMusic = { navActions.navigateToMusic() },
                    navigateToLibrary = { navActions.navigateToLibrary() },
                    navigateToProfile = { navActions.navigateToProfile() },
                    navigateToAlbum = { albumId -> navActions.navigateToAlbum(albumId) },
                    onBack = navActions::popBackStack,
                )
            }

            composable(
                route = AppDestinations.ALBUM_ROUTE,
                arguments = listOf(
                    navArgument(RouteArguments.ALBUM_ID) {
                        type = NavType.LongType
                    },
                ),
            ) {
                AlbumScreen(
                    navigateToMusic = { navActions.navigateToMusic() },
                    navigateToLibrary = { navActions.navigateToLibrary() },
                    navigateToProfile = { navActions.navigateToProfile() },
                    onOpenPlayer = { trackId -> navActions.navigateToPlayer(trackId) },
                    onBack = navActions::popBackStack,
                )
            }

            composable(
                route = AppDestinations.PLAYLIST_ROUTE,
                arguments = listOf(
                    navArgument(RouteArguments.PLAYLIST_ID) {
                        type = NavType.LongType
                    },
                ),
            ) {
                PlaylistScreen(
                    navigateToMusic = { navActions.navigateToMusic() },
                    navigateToLibrary = { navActions.navigateToLibrary() },
                    navigateToProfile = { navActions.navigateToProfile() },
                    onOpenPlayer = { trackId -> navActions.navigateToPlayer(trackId) },
                    onBack = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(RouteArguments.PLAYLIST_LIST_REFRESH_TOKEN, System.nanoTime())
                        navActions.popBackStack()
                    },
                )
            }

            composable(AppDestinations.UPLOAD_ROUTE) {
                UploadScreen(onBack = navActions::popBackStack)
            }

            composable(
                route = AppDestinations.PLAYER_ROUTE,
                arguments = listOf(
                    navArgument(RouteArguments.PLAYER_TRACK_ID) {
                        type = NavType.LongType
                    }
                ),
            ) { backStackEntry ->
                PlayerScreen(
                    requestedTrackId = backStackEntry.arguments?.getLong(RouteArguments.PLAYER_TRACK_ID),
                    onBack = navActions::popBackStack,
                )
            }
        }

        if (showMiniPlayer) {
            MiniPlayerOverlay(
                onOpenPlayer = { trackId -> navActions.navigateToPlayer(trackId) },
                viewModel = playbackViewModel,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }
    }
}

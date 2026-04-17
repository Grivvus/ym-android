package sstu.grivvus.ym

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import sstu.grivvus.ym.AppScreens.ALBUM_SCREEN
import sstu.grivvus.ym.AppScreens.ARTIST_SCREEN
import sstu.grivvus.ym.AppScreens.LIBRARY_SCREEN
import sstu.grivvus.ym.AppScreens.LOGIN_SCREEN
import sstu.grivvus.ym.AppScreens.MUSIC_SCREEN
import sstu.grivvus.ym.AppScreens.PLAYLIST_SCREEN
import sstu.grivvus.ym.AppScreens.PLAYER_SCREEN
import sstu.grivvus.ym.AppScreens.PROFILE_SCREEN
import sstu.grivvus.ym.AppScreens.REGISTRATION_SCREEN
import sstu.grivvus.ym.AppScreens.SERVER_SETUP_SCREEN
import sstu.grivvus.ym.AppScreens.STARTUP_SCREEN
import sstu.grivvus.ym.AppScreens.UPLOAD_SCREEN
import sstu.grivvus.ym.RouteArguments.ALBUM_ID
import sstu.grivvus.ym.RouteArguments.ARTIST_ID
import sstu.grivvus.ym.RouteArguments.PLAYLIST_ID
import sstu.grivvus.ym.RouteArguments.PLAYER_TRACK_ID

object AppScreens {
    const val STARTUP_SCREEN = "startup"
    const val SERVER_SETUP_SCREEN = "server_setup"
    const val LOGIN_SCREEN = "login"
    const val REGISTRATION_SCREEN = "registration"
    const val PROFILE_SCREEN = "profile"
    const val MUSIC_SCREEN = "music"
    const val ARTIST_SCREEN = "artist"
    const val ALBUM_SCREEN = "album"
    const val PLAYLIST_SCREEN = "playlist"
    const val PLAYER_SCREEN = "player"
    const val LIBRARY_SCREEN = "library"
    const val UPLOAD_SCREEN = "upload"
}

object RouteArguments {
    const val PLAYER_TRACK_ID = "trackId"
    const val PLAYLIST_ID = "playlistId"
    const val ARTIST_ID = "artistId"
    const val ALBUM_ID = "albumId"
    const val PLAYLIST_LIST_REFRESH_TOKEN = "playlistListRefreshToken"
}

object AppDestinations {
    const val STARTUP_ROUTE = STARTUP_SCREEN
    const val SERVER_SETUP_ROUTE = SERVER_SETUP_SCREEN
    const val PROFILE_ROUTE = PROFILE_SCREEN
    const val LOGIN_ROUTE = LOGIN_SCREEN
    const val REGISTRATION_ROUTE = REGISTRATION_SCREEN
    const val MUSIC_ROUTE = MUSIC_SCREEN
    const val ARTIST_ROUTE = "$ARTIST_SCREEN/{$ARTIST_ID}"
    const val ALBUM_ROUTE = "$ALBUM_SCREEN/{$ALBUM_ID}"
    const val PLAYLIST_ROUTE = "$PLAYLIST_SCREEN/{$PLAYLIST_ID}"
    const val MAIN_START_ROUTE = MUSIC_ROUTE
    const val PLAYER_ROUTE = "$PLAYER_SCREEN/{$PLAYER_TRACK_ID}"
    const val LIBRARY_ROUTE = LIBRARY_SCREEN
    const val UPLOAD_ROUTE = UPLOAD_SCREEN

    fun playerRoute(trackId: Long): String = "$PLAYER_SCREEN/$trackId"
    fun artistRoute(artistId: Long): String = "$ARTIST_SCREEN/$artistId"
    fun albumRoute(albumId: Long): String = "$ALBUM_SCREEN/$albumId"
    fun playlistRoute(playlistId: Long): String = "$PLAYLIST_SCREEN/$playlistId"
}

class NavigationActions(private val navController: NavController) {
    fun navigateToProfile() {
        navController.navigateToTopLevel(AppDestinations.PROFILE_ROUTE)
    }

    fun navigateToLogin() {
        navController.navigateSingleTopTo(AppDestinations.LOGIN_ROUTE)
    }

    fun navigateToLoginClearingBackStack() {
        navController.navigate(AppDestinations.LOGIN_ROUTE) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun navigateToRegistration() {
        navController.navigateSingleTopTo(AppDestinations.REGISTRATION_ROUTE)
    }

    fun navigateToServerSetup() {
        navController.navigateSingleTopTo(AppDestinations.SERVER_SETUP_ROUTE)
    }

    fun navigateToMusic() {
        navController.navigateToTopLevel(AppDestinations.MUSIC_ROUTE)
    }

    fun navigateToArtist(artistId: Long) {
        navController.navigateSingleTopTo(AppDestinations.artistRoute(artistId))
    }

    fun navigateToAlbum(albumId: Long) {
        navController.navigateSingleTopTo(AppDestinations.albumRoute(albumId))
    }

    fun navigateToMusicFromAuth() {
        navController.navigate(AppDestinations.MUSIC_ROUTE) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun navigateToUpload() {
        navController.navigateSingleTopTo(AppDestinations.UPLOAD_ROUTE)
    }

    fun navigateToPlaylist(playlistId: Long) {
        navController.navigateSingleTopTo(AppDestinations.playlistRoute(playlistId))
    }

    fun navigateToPlayer(trackId: Long) {
        navController.navigateSingleTopTo(AppDestinations.playerRoute(trackId))
    }

    fun navigateToLibrary() {
        navController.navigateToTopLevel(AppDestinations.LIBRARY_ROUTE)
    }

    fun popBackStack() {
        navController.popBackStack()
    }
}

private fun NavController.navigateSingleTopTo(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavController.navigateToTopLevel(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(AppDestinations.MAIN_START_ROUTE) {
            saveState = true
        }
    }
}

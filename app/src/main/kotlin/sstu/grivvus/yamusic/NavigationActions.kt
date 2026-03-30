package sstu.grivvus.yamusic

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import sstu.grivvus.yamusic.AppScreens.LIBRARY_SCREEN
import sstu.grivvus.yamusic.AppScreens.LOGIN_SCREEN
import sstu.grivvus.yamusic.AppScreens.MUSIC_SCREEN
import sstu.grivvus.yamusic.AppScreens.PLAYER_SCREEN
import sstu.grivvus.yamusic.AppScreens.PROFILE_SCREEN
import sstu.grivvus.yamusic.AppScreens.REGISTRATION_SCREEN
import sstu.grivvus.yamusic.AppScreens.SERVER_SETUP_SCREEN
import sstu.grivvus.yamusic.AppScreens.STARTUP_SCREEN
import sstu.grivvus.yamusic.AppScreens.UPLOAD_SCREEN
import sstu.grivvus.yamusic.RouteArguments.PLAYER_TRACK_ID

object AppScreens {
    const val STARTUP_SCREEN = "startup"
    const val SERVER_SETUP_SCREEN = "server_setup"
    const val LOGIN_SCREEN = "login"
    const val REGISTRATION_SCREEN = "registration"
    const val PROFILE_SCREEN = "profile"
    const val MUSIC_SCREEN = "music"
    const val PLAYER_SCREEN = "player"
    const val LIBRARY_SCREEN = "library"
    const val UPLOAD_SCREEN = "upload"
}

object RouteArguments {
    const val PLAYER_TRACK_ID = "trackId"
}

object AppDestinations {
    const val STARTUP_ROUTE = STARTUP_SCREEN
    const val SERVER_SETUP_ROUTE = SERVER_SETUP_SCREEN
    const val PROFILE_ROUTE = PROFILE_SCREEN
    const val LOGIN_ROUTE = LOGIN_SCREEN
    const val REGISTRATION_ROUTE = REGISTRATION_SCREEN
    const val MUSIC_ROUTE = MUSIC_SCREEN
    const val MAIN_START_ROUTE = MUSIC_ROUTE
    const val PLAYER_ROUTE = "$PLAYER_SCREEN/{$PLAYER_TRACK_ID}"
    const val LIBRARY_ROUTE = LIBRARY_SCREEN
    const val UPLOAD_ROUTE = UPLOAD_SCREEN

    fun playerRoute(trackId: Long): String = "$PLAYER_SCREEN/$trackId"
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

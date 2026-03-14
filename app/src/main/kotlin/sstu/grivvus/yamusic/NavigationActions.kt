package sstu.grivvus.yamusic

import androidx.navigation.NavController
import sstu.grivvus.yamusic.AppScreens.LIBRARY_SCREEN
import sstu.grivvus.yamusic.AppScreens.LOGIN_SCREEN
import sstu.grivvus.yamusic.AppScreens.MUSIC_SCREEN
import sstu.grivvus.yamusic.AppScreens.PROFILE_SCREEN
import sstu.grivvus.yamusic.AppScreens.REGISTRATION_SCREEN
import sstu.grivvus.yamusic.AppScreens.SERVER_SETUP_SCREEN
import sstu.grivvus.yamusic.AppScreens.STARTUP_SCREEN
import sstu.grivvus.yamusic.AppScreens.UPLOAD_SCREEN
import sstu.grivvus.yamusic.RouteArguments.PLAYER_ARGS

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
    const val PLAYER_ARGS = "{track_id}"
}

object AppDestinations {
    const val STARTUP_ROUTE = STARTUP_SCREEN
    const val SERVER_SETUP_ROUTE = SERVER_SETUP_SCREEN
    const val PROFILE_ROUTE = PROFILE_SCREEN
    const val LOGIN_ROUTE = LOGIN_SCREEN
    const val REGISTRATION_ROUTE = REGISTRATION_SCREEN
    const val MUSIC_ROUTE = MUSIC_SCREEN
    const val PLAYER_ROUTE = "$MUSIC_SCREEN/$PLAYER_ARGS"
    const val LIBRARY_ROUTE = LIBRARY_SCREEN
    const val UPLOAD_ROUTE = UPLOAD_SCREEN
}

class NavigationActions(val navController: NavController) {
    fun navigateToProfile() {
        navController.navigate(AppDestinations.PROFILE_ROUTE)
    }

    fun navigateToLogin() {
        navController.navigate(AppDestinations.LOGIN_ROUTE)
    }

    fun navigateToRegistration() {
        navController.navigate(AppDestinations.REGISTRATION_ROUTE)
    }

    fun navigateToServerSetup() {
        navController.navigate(AppDestinations.SERVER_SETUP_ROUTE)
    }

    fun navigateToMusic() {
        navController.navigate(AppDestinations.MUSIC_ROUTE)
    }

    fun navigateToUpload() {
        navController.navigate(AppDestinations.UPLOAD_ROUTE)
    }

    fun navigateToLibrary() {
        navController.navigate(AppDestinations.LIBRARY_ROUTE)
    }
}

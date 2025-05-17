package sstu.grivvus.yamusic

import androidx.navigation.NavController
import sstu.grivvus.yamusic.AppScreens.LOGIN_SCREEN
import sstu.grivvus.yamusic.AppScreens.MUSIC_SCREEN
import sstu.grivvus.yamusic.AppScreens.PROFILE_SCREEN
import sstu.grivvus.yamusic.AppScreens.REGISTRATION_SCREEN

object AppScreens {
    const val LOGIN_SCREEN = "login"
    const val REGISTRATION_SCREEN = "registration"
    const val PROFILE_SCREEN = "profile"
    const val MUSIC_SCREEN = "music"
}

object AppDestinations {
    const val PROFILE_ROUTE = PROFILE_SCREEN
    const val LOGIN_ROUTE = LOGIN_SCREEN
    const val REGISTRATION_ROUTE = REGISTRATION_SCREEN;
    const val MUSIC_ROUTE = MUSIC_SCREEN
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

    fun navigateToMusic() {
        navController.navigate(AppDestinations.MUSIC_ROUTE)
    }
}
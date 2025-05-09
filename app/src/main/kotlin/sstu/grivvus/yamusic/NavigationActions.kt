package sstu.grivvus.yamusic

import androidx.navigation.NavController
import sstu.grivvus.yamusic.AppScreens.LOGIN_SCREEN
import sstu.grivvus.yamusic.AppScreens.PROFILE_SCREEN

object AppScreens {
    const val LOGIN_SCREEN = "login"
    const val PROFILE_SCREEN = "profile"
}

object AppDestinations {
    const val PROFILE_ROUTE = PROFILE_SCREEN
    const val LOGIN_ROUTE = LOGIN_SCREEN
}

class NavigationActions(val navController: NavController) {
    fun navigateToProfile() {
        navController.navigate(AppDestinations.PROFILE_ROUTE)
    }

    fun navigateToLogin()  {
        navController.navigate(AppDestinations.LOGIN_ROUTE)
    }

    fun navigateToMusic() {
        TODO()
    }

    fun navigateToWeather() {
        TODO()
    }
}
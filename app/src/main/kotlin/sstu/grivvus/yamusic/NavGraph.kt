package sstu.grivvus.yamusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import sstu.grivvus.yamusic.components.BlankScreen
import sstu.grivvus.yamusic.login.LoginScreen
import sstu.grivvus.yamusic.profile.ProfileScreen
import sstu.grivvus.yamusic.register.RegistrationScreen

@Composable
fun YaMusicNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    startDestination: String = AppDestinations.REGISTRATION_ROUTE,
    navActions: NavigationActions = remember(navController){
        NavigationActions(navController)
    }
) {
    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStackEntry?.destination?.route ?: startDestination

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                {navActions.navigateToRegistration()},
                {navActions.navigateToProfile()}
            )
        }
        composable(AppDestinations.REGISTRATION_ROUTE) {
            RegistrationScreen(
                { navActions.navigateToLogin() },
                { navActions.navigateToProfile() },
            )
        }
        composable(AppDestinations.PROFILE_ROUTE) {
            ProfileScreen(
                { navActions.navigateToMusic() },
                { navActions.navigateToLibrary() },
                { navActions.navigateToProfile() },
                { navActions.navigateToLogin() }
            )
        }

        composable(AppDestinations.LIBRARY_ROUTE) {
            BlankScreen(navActions)
        }

        composable(AppDestinations.MUSIC_ROUTE) {
            BlankScreen(navActions)
        }
    }
}
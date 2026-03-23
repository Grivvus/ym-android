package sstu.grivvus.yamusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import sstu.grivvus.yamusic.components.BlankScreen
import sstu.grivvus.yamusic.login.LoginScreen
import sstu.grivvus.yamusic.music.MusicScreen
import sstu.grivvus.yamusic.music.UploadScreen
import sstu.grivvus.yamusic.profile.ProfileScreen
import sstu.grivvus.yamusic.register.RegistrationScreen
import sstu.grivvus.yamusic.serverSetup.ServerSetup
import sstu.grivvus.yamusic.startup.StartupScreen

@Composable
fun YaMusicNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    startDestination: String = AppDestinations.STARTUP_ROUTE,
    navActions: NavigationActions = remember(navController) {
        NavigationActions(navController)
    }
) {
    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    currentNavBackStackEntry?.destination?.route ?: startDestination

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppDestinations.STARTUP_ROUTE) {
            StartupScreen(
                onRouteResolved = { route ->
                    navController.navigate(route) {
                        popUpTo(AppDestinations.STARTUP_ROUTE) {
                            inclusive = true
                        }
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
                    }
                },
            )
        }
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                { navActions.navigateToRegistration() },
                { navActions.navigateToMusic() }
            )
        }
        composable(AppDestinations.REGISTRATION_ROUTE) {
            RegistrationScreen(
                { navActions.navigateToLogin() },
                { navActions.navigateToMusic() },
            )
        }
        composable(AppDestinations.PROFILE_ROUTE) {
            ProfileScreen(
                { navActions.navigateToMusic() },
                { navActions.navigateToLibrary() },
                { navActions.navigateToProfile() },
                { navActions.navigateToLogin() },
            )
        }

        composable(AppDestinations.LIBRARY_ROUTE) {
            BlankScreen(navActions)
        }

        composable(AppDestinations.MUSIC_ROUTE) {
            MusicScreen(
                { navActions.navigateToMusic() },
                { navActions.navigateToLibrary() },
                { navActions.navigateToProfile() },
            )
        }

        composable(AppDestinations.UPLOAD_ROUTE) {
            UploadScreen(navController)
        }

//        composable(
//            AppDestinations.PLAYER_ROUTE,
//        ) {  backStackEntry ->
//            AudioPlayerScreen(
//                { navActions.navigateToMusic() },
//                { navActions.navigateToLibrary() },
//                { navActions.navigateToProfile() },
//                backStackEntry.arguments?.getString("trackUri") ?: "-1"
//            )
//        }
    }
}

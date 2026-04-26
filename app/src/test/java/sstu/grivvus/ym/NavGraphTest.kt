package sstu.grivvus.ym

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import sstu.grivvus.ym.data.network.auth.SessionEndReason
import sstu.grivvus.ym.data.network.auth.SessionState
import sstu.grivvus.ym.data.network.model.NetworkSession

class NavGraphTest {
    @Test
    fun shouldRedirectToLogin_returnsTrueForUnauthenticatedProtectedRoute() {
        assertThat(
            shouldRedirectToLogin(
                sessionState = SessionState.Unauthenticated(SessionEndReason.EXPIRED),
                currentRoute = AppDestinations.MUSIC_ROUTE,
            )
        ).isTrue()
    }

    @Test
    fun shouldRedirectToLogin_returnsFalseForUnauthenticatedPublicRoute() {
        assertThat(
            shouldRedirectToLogin(
                sessionState = SessionState.Unauthenticated(),
                currentRoute = AppDestinations.LOGIN_ROUTE,
            )
        ).isFalse()
    }

    @Test
    fun shouldRedirectToLogin_returnsFalseForUnauthenticatedPasswordResetRoute() {
        assertThat(
            shouldRedirectToLogin(
                sessionState = SessionState.Unauthenticated(),
                currentRoute = AppDestinations.PASSWORD_RESET_ROUTE,
            )
        ).isFalse()
    }

    @Test
    fun shouldRedirectToLogin_returnsFalseForAuthenticatedProtectedRoute() {
        assertThat(
            shouldRedirectToLogin(
                sessionState = SessionState.Authenticated(
                    session = NetworkSession(
                        userId = 42L,
                        accessToken = "token",
                        refreshToken = "refresh",
                    ),
                ),
                currentRoute = AppDestinations.PROFILE_ROUTE,
            )
        ).isFalse()
    }

    @Test
    fun appProtectedRoutes_containsAllProtectedDestinations() {
        assertThat(appProtectedRoutes()).containsExactly(
            AppDestinations.MUSIC_ROUTE,
            AppDestinations.ARTIST_ROUTE,
            AppDestinations.ALBUM_ROUTE,
            AppDestinations.PLAYLIST_ROUTE,
            AppDestinations.PROFILE_ROUTE,
            AppDestinations.LIBRARY_ROUTE,
            AppDestinations.UPLOAD_ROUTE,
            AppDestinations.PLAYER_ROUTE,
        )
    }
}

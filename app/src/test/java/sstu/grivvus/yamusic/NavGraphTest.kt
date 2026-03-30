package sstu.grivvus.yamusic

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import sstu.grivvus.yamusic.data.network.SessionEndReason
import sstu.grivvus.yamusic.data.network.SessionState

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
    fun shouldRedirectToLogin_returnsFalseForAuthenticatedProtectedRoute() {
        assertThat(
            shouldRedirectToLogin(
                sessionState = SessionState.Authenticated(userId = 42L),
                currentRoute = AppDestinations.PROFILE_ROUTE,
            )
        ).isFalse()
    }

    @Test
    fun appProtectedRoutes_containsAllProtectedDestinations() {
        assertThat(appProtectedRoutes()).containsExactly(
            AppDestinations.MUSIC_ROUTE,
            AppDestinations.PROFILE_ROUTE,
            AppDestinations.LIBRARY_ROUTE,
            AppDestinations.UPLOAD_ROUTE,
            AppDestinations.PLAYER_ROUTE,
        )
    }
}

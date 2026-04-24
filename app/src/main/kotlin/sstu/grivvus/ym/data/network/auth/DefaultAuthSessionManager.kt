package sstu.grivvus.ym.data.network.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.local.AlbumDao
import sstu.grivvus.ym.data.local.ArtistDao
import sstu.grivvus.ym.data.local.AudioTrackDao
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.PlaylistDao
import sstu.grivvus.ym.data.local.PlaylistTrackDao
import sstu.grivvus.ym.data.local.TrackAlbumDao
import sstu.grivvus.ym.data.local.UserDao
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.di.ApplicationScope
import sstu.grivvus.ym.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthSessionManager @Inject constructor(
    private val userDao: UserDao,
    private val audioTrackDao: AudioTrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val trackAlbumDao: TrackAlbumDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val tokenRefresher: TokenRefresher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) : AuthSessionManager {
    private val refreshMutex = Mutex()
    private val internalState = MutableStateFlow<SessionState>(SessionState.Initializing)

    override val sessionState: StateFlow<SessionState> = internalState.asStateFlow()

    init {
        applicationScope.launch(ioDispatcher) {
            syncSessionStateFromStorage()
        }
    }

    override suspend fun startSession(session: NetworkSession) {
        val existingUser = withContext(ioDispatcher) {
            userDao.getActiveUser()
        }
        val persistedUser = session.toLocalUser(previousUser = existingUser)
        withContext(ioDispatcher) {
            userDao.clearTable()
            userDao.insert(persistedUser)
        }
        internalState.value = SessionState.Authenticated(session)
    }

    override suspend fun currentSessionOrNull(): NetworkSession? {
        return withContext(ioDispatcher) {
            userDao.getActiveUser()?.toNetworkSession()
        }
    }

    override suspend fun requireSession(): NetworkSession {
        return currentSessionOrNull() ?: run {
            markSessionExpired()
            throw SessionExpiredException()
        }
    }

    override suspend fun resolveAccessToken(): String? {
        return currentSessionOrNull()
            ?.accessToken
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun refreshAfterUnauthorized(attemptedAccessToken: String?): String? {
        return refreshMutex.withLock {
            val currentSession = currentSessionOrNull() ?: run {
                markSessionExpired()
                return@withLock null
            }

            val currentAccessToken = currentSession.accessToken.takeIf { it.isNotBlank() }
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != attemptedAccessToken) {
                return@withLock currentAccessToken
            }

            val refreshToken = currentSession.refreshToken.takeIf { it.isNotBlank() } ?: run {
                markSessionExpired()
                return@withLock null
            }

            try {
                val refreshedSession = tokenRefresher.refresh(refreshToken)
                startSession(refreshedSession)
                refreshedSession.accessToken
            } catch (error: ApiException) {
                if (error.statusCode == 401) {
                    markSessionExpired()
                    return@withLock null
                } else {
                    throw error
                }
            }
        }
    }

    override suspend fun getCurrentUser(): LocalUser? {
        return withContext(ioDispatcher) {
            userDao.getActiveUser()
        }
    }

    override suspend fun requireCurrentUser(): LocalUser {
        return getCurrentUser() ?: run {
            markSessionExpired()
            throw SessionExpiredException()
        }
    }

    override suspend fun updateCurrentUser(user: LocalUser) {
        withContext(ioDispatcher) {
            userDao.upsert(user)
        }
        internalState.value = user.toNetworkSession().toSessionState()
    }

    override suspend fun clearSession(reason: SessionEndReason) {
        withContext(ioDispatcher) {
            userDao.clearTable()
            playlistTrackDao.clearAll()
            trackAlbumDao.clearAll()
            audioTrackDao.clearAll()
            albumDao.clearAll()
            artistDao.clearAll()
            playlistDao.clearAll()
        }
        internalState.value = SessionState.Unauthenticated(reason)
    }

    override suspend fun markSessionExpired() {
        clearSession(SessionEndReason.EXPIRED)
    }

    override suspend fun logout() {
        clearSession(SessionEndReason.LOGOUT)
    }

    private suspend fun syncSessionStateFromStorage() {
        val currentSession = currentSessionOrNull()
        internalState.value = currentSession?.let(SessionState::Authenticated)
            ?: SessionState.Unauthenticated()
    }

    private fun NetworkSession.toSessionState(): SessionState.Authenticated {
        return SessionState.Authenticated(this)
    }

    private fun NetworkSession.toLocalUser(previousUser: LocalUser?): LocalUser {
        val preservedUser = previousUser?.takeIf { it.remoteId == userId }
        return LocalUser(
            remoteId = userId,
            username = preservedUser?.username.orEmpty(),
            email = preservedUser?.email,
            access = accessToken,
            refresh = refreshToken,
            isSuperuser = preservedUser?.isSuperuser ?: false,
            avatarUri = preservedUser?.avatarUri,
        )
    }

    private fun LocalUser.toNetworkSession(): NetworkSession {
        return NetworkSession(
            userId = remoteId,
            accessToken = access.orEmpty(),
            refreshToken = refresh.orEmpty(),
        )
    }
}

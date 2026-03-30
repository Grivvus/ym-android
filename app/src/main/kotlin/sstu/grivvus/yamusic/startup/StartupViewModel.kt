package sstu.grivvus.yamusic.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.AppDestinations
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.UserRepository
import javax.inject.Inject

data class StartupUiState(
    val targetRoute: String? = null,
)

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val serverInfoRepository: ServerInfoRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StartupUiState())
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val serverInfo = serverInfoRepository.getServerInfo()
            val userInfo = userRepository.getCurrentUser()
            val route =
                if (serverInfo == null) {
                    AppDestinations.SERVER_SETUP_ROUTE
                } else if (userInfo == null) {
                    AppDestinations.REGISTRATION_ROUTE
                } else {
                    AppDestinations.MUSIC_ROUTE
                }
            _uiState.value = StartupUiState(targetRoute = route)
        }
    }
}

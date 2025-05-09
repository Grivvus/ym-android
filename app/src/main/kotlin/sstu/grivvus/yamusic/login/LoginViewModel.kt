package sstu.grivvus.yamusic.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.data.UserRepository
import javax.inject.Inject

class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
}
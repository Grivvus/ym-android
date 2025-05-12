package sstu.grivvus.yamusic.register

import androidx.lifecycle.ViewModel
import sstu.grivvus.yamusic.data.UserRepository
import javax.inject.Inject

class RegisterViewModel @Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
}
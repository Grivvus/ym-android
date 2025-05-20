package sstu.grivvus.yamusic.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.data.UserRepository
import sstu.grivvus.yamusic.data.network.NetworkUser
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userRepository: UserRepository
):  ViewModel() {
    fun proceedRegistration(
        username: String,
        password: String,
        checkPassword: String,
    ) = viewModelScope.launch {
        if (checkPassword != password){
            TODO("Show up some message")
        }
        userRepository.register(NetworkUserCreate(username, null, password))
    }
}
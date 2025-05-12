package sstu.grivvus.yamusic.register

import sstu.grivvus.yamusic.login.LoginViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.automirrored.sharp.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.login.LoginFormField

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    RegisterScreenUI()
}

@Composable
fun RegisterScreenUI(modifier: Modifier = Modifier) {
    var loginInput = remember { mutableStateOf("") }
    var passwordInput = remember { mutableStateOf("") }
    var checkPasswordInput = remember { mutableStateOf("") }

    YaMusicTheme {
        Box(modifier.fillMaxSize()) {
            Column(
                modifier.padding(top = 100.dp, bottom = 100.dp, start = 50.dp, end = 50.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(modifier.fillMaxWidth()){
                    LoginFormField(appIcons.Login, loginInput, placeholderText = "Login")
                }
                Spacer(modifier.height(5.dp))
                Row(modifier.fillMaxWidth()) {
                    LoginFormField(appIcons.StarHalf, passwordInput, placeholderText = "Password")
                }
                Spacer(modifier.height(5.dp))
                Row(modifier.fillMaxWidth()) {
                    LoginFormField(appIcons.StarHalf, checkPasswordInput, placeholderText = "Confirm password")
                }
                Spacer(modifier.height(25.dp))
                Row(modifier = modifier.fillMaxWidth().padding(end = 15.dp), horizontalArrangement = Arrangement.End){
                    Text("Sign In", modifier.clickable(enabled = true, onClick = {
                        TODO("go to login screen")
                    }))
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Button({}) { Text("Proceed") }
                    Button({}) { Text("Cancel")}
                }

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    RegisterScreenUI()
}
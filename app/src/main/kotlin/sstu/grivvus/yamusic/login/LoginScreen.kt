package sstu.grivvus.yamusic.login

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
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.sharp.Lock
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.yamusic.components.ErrorTooltip
import sstu.grivvus.yamusic.components.LoginFormField
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.ui.theme.appIconsMirrored

@Composable
fun LoginScreen(
    onSignUpClick: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YaMusicTheme {
        Box(modifier.fillMaxSize()) {
            ErrorTooltip(
                uiState.errorMessage ?: "",
                uiState.showError,
                onTimeout = { viewModel.dismissErrorMessage() },
            )
            Column(
                modifier
                    .padding(top = 100.dp, bottom = 100.dp, start = 50.dp, end = 50.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoginFormField(
                        appIconsMirrored.Login,
                        uiState.username,
                        { viewModel.updateUsername(it) },
                        "Login",
                    )
                }
                Spacer(modifier.height(5.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoginFormField(
                        appIcons.Password,
                        uiState.password,
                        { viewModel.updatePassword(it) },
                        "Password",
                        true,
                    )
                }
                Spacer(modifier.height(25.dp))
                Row(
                    modifier = modifier.fillMaxWidth().padding(end = 15.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "Sign Up",
                        modifier.clickable(enabled = true, onClick = {
                            onSignUpClick()
                        }),
                    )
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Button({
                        viewModel.proceedLogin(onSuccess)
                    }) { Text("Proceed") }
                    Button({ viewModel.clearForm() }) { Text("Clear") }
                }
            }
        }
    }
}
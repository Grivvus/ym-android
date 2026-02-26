package sstu.grivvus.yamusic.serverSetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.yamusic.components.ErrorTooltip
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.ui.theme.appIconsMirrored

@Composable
fun ServerSetup(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    YaMusicTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ErrorTooltip(
                    uiState.errorMessage ?: "",
                    uiState.showError,
                    onTimeout = { viewModel.dismissErrorMessage() },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = { viewModel.updateHost(it) },
                        label = { Text("server host") },
                        leadingIcon = {
                            Icon(
                                imageVector = appIconsMirrored.Login,
                                contentDescription = "Username icon"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = uiState.port.toString(),
                        onValueChange = { viewModel.updatePort(it) },
                        label = { Text("server port") },
                        leadingIcon = {
                            Icon(
                                imageVector = appIcons.Password,
                                contentDescription = "Password icon"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearForm() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text("Clear")
                        }
                        Button(
                            onClick = { viewModel.proceed(onProceed) },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.host.isNotBlank() &&
                                    uiState.port.toString().isNotBlank()
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}
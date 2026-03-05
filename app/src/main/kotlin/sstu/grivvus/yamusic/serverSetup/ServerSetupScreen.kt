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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.yamusic.components.ErrorTooltip
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
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
                        text = "Server Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = { viewModel.updateHost(it) },
                        label = { Text("Server host or URL") },
                        leadingIcon = {
                            Icon(
                                imageVector = appIconsMirrored.Login,
                                contentDescription = "Server host icon"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = { viewModel.updatePort(it) },
                        label = { Text("Server port") },
                        leadingIcon = {
                            Icon(
                                imageVector = appIconsMirrored.Login,
                                contentDescription = "Server port icon"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
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
                            enabled = !uiState.isLoading && (uiState.host.isNotBlank() || uiState.port.isNotBlank()),
                        ) {
                            Text("Clear")
                        }
                        Button(
                            onClick = { viewModel.proceed(onProceed) },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading &&
                                    uiState.host.isNotBlank() &&
                                    uiState.port.isNotBlank()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Test & Save")
                            }
                        }
                    }

                    if (!uiState.isLoading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The app will use this server for all requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

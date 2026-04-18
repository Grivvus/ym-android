package sstu.grivvus.ym.serverSetup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.components.CenteredFormScreen
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.FormActionRow
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIconsMirrored

@Composable
fun ServerSetup(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    YMTheme {
        CenteredFormScreen(
            modifier = modifier,
            overlay = {
                ErrorTooltip(
                    uiState.errorMessage ?: "",
                    uiState.showError,
                    onTimeout = { viewModel.dismissErrorMessage() },
                )
            },
        ) {
            Text(
                text = "Server Setup",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.host,
                onValueChange = { viewModel.updateHost(it) },
                label = { Text("Server host or URL") },
                leadingIcon = {
                    Icon(
                        imageVector = appIconsMirrored.Login,
                        contentDescription = "Server host icon",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = uiState.port,
                onValueChange = { viewModel.updatePort(it) },
                label = { Text("Server port") },
                leadingIcon = {
                    Icon(
                        imageVector = appIconsMirrored.Login,
                        contentDescription = "Server port icon",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            FormActionRow(
                secondaryButtonLabel = "Clear",
                onSecondaryButtonClick = { viewModel.clearForm() },
                primaryButtonLabel = "Test & Save",
                onPrimaryButtonClick = { viewModel.proceed(onProceed) },
                secondaryButtonEnabled = !uiState.isLoading &&
                        (uiState.host.isNotBlank() || uiState.port.isNotBlank()),
                primaryButtonEnabled = !uiState.isLoading &&
                        uiState.host.isNotBlank() &&
                        uiState.port.isNotBlank(),
                isPrimaryButtonLoading = uiState.isLoading,
            )

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

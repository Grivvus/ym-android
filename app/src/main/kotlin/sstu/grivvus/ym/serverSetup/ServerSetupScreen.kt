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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.CenteredFormScreen
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.FormActionRow
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIconsMirrored

@Composable
fun ServerSetup(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CenteredFormScreen(
        modifier = modifier,
        overlay = {
            ErrorTooltip(
                message = uiState.errorMessage?.resolve().orEmpty(),
                visible = uiState.showError,
                modifier = Modifier.align(Alignment.BottomCenter),
                onDismiss = viewModel::dismissErrorMessage,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.server_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.host,
            onValueChange = { viewModel.updateHost(it) },
            label = { Text(stringResource(R.string.server_setup_label_host_or_url)) },
            leadingIcon = {
                Icon(
                    imageVector = appIconsMirrored.Login,
                    contentDescription = stringResource(R.string.server_setup_cd_server_host_icon),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = uiState.port,
            onValueChange = { viewModel.updatePort(it) },
            label = { Text(stringResource(R.string.common_label_server_port)) },
            leadingIcon = {
                Icon(
                    imageVector = appIconsMirrored.Login,
                    contentDescription = stringResource(R.string.server_setup_cd_server_port_icon),
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
            secondaryButtonLabel = stringResource(R.string.common_action_clear),
            onSecondaryButtonClick = { viewModel.clearForm() },
            primaryButtonLabel = stringResource(R.string.common_action_proceed),
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
                text = stringResource(R.string.server_setup_description_all_requests),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

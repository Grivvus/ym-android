package sstu.grivvus.ym.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.CenteredFormScreen
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.FormActionRow
import sstu.grivvus.ym.components.PasswordOutlinedField
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons
import sstu.grivvus.ym.ui.theme.appIconsMirrored

@Composable
fun LoginScreen(
    onSignUpClick: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CenteredFormScreen(
        modifier = modifier,
        overlay = {
            ErrorTooltip(
                uiState.errorMessage?.resolve().orEmpty(),
                uiState.showError,
                onTimeout = { viewModel.dismissErrorMessage() },
            )
        },
    ) {
        Text(
            text = stringResource(R.string.login_title_sign_in_account),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = { viewModel.updateUsername(it) },
            label = { Text(stringResource(R.string.login_label_login)) },
            leadingIcon = {
                Icon(
                    imageVector = appIconsMirrored.Login,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        PasswordOutlinedField(
            value = uiState.password,
            onValueChange = { viewModel.updatePassword(it) },
            label = stringResource(R.string.common_label_password),
            leadingIcon = appIcons.Password,
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Done,
        )

        TextButton(
            onClick = onSignUpClick,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.common_action_sign_up))
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormActionRow(
            secondaryButtonLabel = stringResource(R.string.common_action_clear),
            onSecondaryButtonClick = { viewModel.clearForm() },
            primaryButtonLabel = stringResource(R.string.common_action_login),
            onPrimaryButtonClick = { viewModel.proceedLogin(onSuccess) },
            primaryButtonEnabled = uiState.username.isNotBlank() && uiState.password.isNotBlank(),
        )
    }
}

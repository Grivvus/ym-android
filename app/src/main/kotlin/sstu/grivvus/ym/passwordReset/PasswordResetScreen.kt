package sstu.grivvus.ym.passwordReset

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.sharp.Email
import androidx.compose.material.icons.sharp.LockReset
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.CenteredFormScreen
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.FormActionRow
import sstu.grivvus.ym.components.PasswordOutlinedField
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons

private const val RETURN_TO_LOGIN_DELAY_MILLIS = 1200L

@Composable
fun PasswordResetScreen(
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasswordResetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.resetCompleted) {
        if (uiState.resetCompleted) {
            delay(RETURN_TO_LOGIN_DELAY_MILLIS)
            viewModel.clearForm()
            onBackToSignIn()
        }
    }

    CenteredFormScreen(
        modifier = modifier,
        verticalSpacing = 12.dp,
        overlay = {
            ErrorTooltip(
                uiState.errorMessage?.resolve().orEmpty(),
                uiState.showError,
                onTimeout = { viewModel.dismissErrorMessage() },
            )
        },
    ) {
        Text(
            text = stringResource(R.string.password_reset_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource(R.string.password_reset_warning_email_required_in_profile),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.infoMessage?.let { message ->
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (uiState.step) {
            PasswordResetStep.RequestCode -> PasswordResetRequestCodeContent(
                uiState = uiState,
                onEmailChange = viewModel::updateEmail,
                onClear = viewModel::clearForm,
                onRequestCode = viewModel::requestResetCode,
                onBackToSignIn = onBackToSignIn,
            )

            PasswordResetStep.ConfirmReset -> PasswordResetConfirmContent(
                uiState = uiState,
                onCodeChange = viewModel::updateCode,
                onNewPasswordChange = viewModel::updateNewPassword,
                onConfirmPasswordChange = viewModel::updateConfirmPassword,
                onUseAnotherEmail = viewModel::returnToRequestStep,
                onConfirm = viewModel::confirmPasswordReset,
            )
        }
    }
}

@Composable
private fun ColumnScope.PasswordResetRequestCodeContent(
    uiState: PasswordResetUiState,
    onEmailChange: (String) -> Unit,
    onClear: () -> Unit,
    onRequestCode: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    EmailField(
        value = uiState.email,
        onValueChange = onEmailChange,
        enabled = !uiState.isLoading,
        imeAction = ImeAction.Done,
    )

    TextButton(
        onClick = onBackToSignIn,
        modifier = Modifier.align(Alignment.End),
        enabled = !uiState.isLoading,
    ) {
        Text(stringResource(R.string.common_action_sign_in))
    }

    Spacer(modifier = Modifier.height(4.dp))

    FormActionRow(
        secondaryButtonLabel = stringResource(R.string.common_action_clear),
        onSecondaryButtonClick = onClear,
        primaryButtonLabel = stringResource(R.string.password_reset_action_request_code),
        onPrimaryButtonClick = onRequestCode,
        secondaryButtonEnabled = !uiState.isLoading && uiState.email.isNotBlank(),
        primaryButtonEnabled = !uiState.isLoading && uiState.email.isNotBlank(),
        isPrimaryButtonLoading = uiState.isLoading,
    )
}

@Composable
private fun ColumnScope.PasswordResetConfirmContent(
    uiState: PasswordResetUiState,
    onCodeChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onUseAnotherEmail: () -> Unit,
    onConfirm: () -> Unit,
) {
    EmailField(
        value = uiState.email,
        onValueChange = {},
        enabled = false,
        imeAction = ImeAction.Next,
    )

    OutlinedTextField(
        value = uiState.code,
        onValueChange = onCodeChange,
        label = { Text(stringResource(R.string.password_reset_label_code)) },
        leadingIcon = {
            Icon(
                imageVector = appIcons.LockReset,
                contentDescription = null,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isLoading,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )

    PasswordOutlinedField(
        value = uiState.newPassword,
        onValueChange = onNewPasswordChange,
        label = stringResource(R.string.common_label_new_password),
        leadingIcon = appIcons.Password,
        modifier = Modifier.fillMaxWidth(),
        imeAction = ImeAction.Next,
        enabled = !uiState.isLoading,
    )

    PasswordOutlinedField(
        value = uiState.newPasswordConfirm,
        onValueChange = onConfirmPasswordChange,
        label = stringResource(R.string.common_label_confirm_password),
        leadingIcon = appIcons.LockReset,
        modifier = Modifier.fillMaxWidth(),
        imeAction = ImeAction.Done,
        enabled = !uiState.isLoading,
    )

    TextButton(
        onClick = onUseAnotherEmail,
        modifier = Modifier.align(Alignment.End),
        enabled = !uiState.isLoading,
    ) {
        Text(stringResource(R.string.password_reset_action_use_another_email))
    }

    Spacer(modifier = Modifier.height(4.dp))

    FormActionRow(
        secondaryButtonLabel = stringResource(R.string.common_action_back),
        onSecondaryButtonClick = onUseAnotherEmail,
        primaryButtonLabel = stringResource(R.string.password_reset_action_reset_password),
        onPrimaryButtonClick = onConfirm,
        secondaryButtonEnabled = !uiState.isLoading,
        primaryButtonEnabled = !uiState.isLoading &&
                uiState.email.isNotBlank() &&
                uiState.code.isNotBlank() &&
                uiState.newPassword.isNotBlank() &&
                uiState.newPasswordConfirm.isNotBlank(),
        isPrimaryButtonLoading = uiState.isLoading,
    )
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.common_label_email)) },
        leadingIcon = {
            Icon(
                imageVector = appIcons.Email,
                contentDescription = null,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = KeyboardType.Email,
        ),
    )
}

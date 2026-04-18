package sstu.grivvus.ym.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.sharp.LockReset
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.components.CenteredFormScreen
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.FormActionRow
import sstu.grivvus.ym.components.PasswordOutlinedField
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIcons
import sstu.grivvus.ym.ui.theme.appIconsMirrored

@Composable
fun RegistrationScreen(
    onSignInClick: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
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
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text("Username") },
                leadingIcon = {
                    Icon(
                        imageVector = appIconsMirrored.Login,
                        contentDescription = "Username icon",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            PasswordOutlinedField(
                value = uiState.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = "Password",
                leadingIcon = appIcons.Password,
                leadingIconContentDescription = "Password icon",
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Next,
            )
            PasswordOutlinedField(
                value = uiState.passwordCheck,
                onValueChange = { viewModel.updatePasswordCheck(it) },
                label = "Confirm Password",
                leadingIcon = appIcons.LockReset,
                leadingIconContentDescription = "Confirm password icon",
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Done,
            )

            TextButton(
                onClick = onSignInClick,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Sign In")
            }

            Spacer(modifier = Modifier.height(16.dp))

            FormActionRow(
                secondaryButtonLabel = "Clear",
                onSecondaryButtonClick = { viewModel.clearForm() },
                primaryButtonLabel = "Register",
                onPrimaryButtonClick = { viewModel.proceedRegistration(onSuccess) },
                primaryButtonEnabled = uiState.username.isNotBlank() &&
                        uiState.password.isNotBlank() &&
                        uiState.passwordCheck.isNotBlank(),
            )
        }
    }
}

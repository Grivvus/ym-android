package sstu.grivvus.ym.passwordChange

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.sharp.Lock
import androidx.compose.material.icons.sharp.LockReset
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.PasswordOutlinedField
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons

@Composable
fun PasswordChangeDialog(
    viewModel: PasswordChangeViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val successMessage = stringResource(R.string.password_change_success)

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            delay(1000)
            viewModel.clear()
            onDismiss()
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!uiState.isLoading) {
                viewModel.clear()
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.password_change_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                PasswordOutlinedField(
                    value = uiState.currentPassword,
                    onValueChange = viewModel::updateCurrentPassword,
                    label = stringResource(R.string.common_label_current_password),
                    leadingIcon = appIcons.Lock,
                    leadingIconContentDescription = stringResource(R.string.common_label_current_password),
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Next,
                    isError = uiState.errorMessage != null,
                )

                Spacer(Modifier.height(16.dp))

                PasswordOutlinedField(
                    value = uiState.newPassword,
                    onValueChange = viewModel::updateNewPassword,
                    label = stringResource(R.string.common_label_new_password),
                    leadingIcon = appIcons.Password,
                    leadingIconContentDescription = stringResource(R.string.common_label_new_password),
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Next,
                    isError = uiState.errorMessage != null,
                )

                Spacer(Modifier.height(16.dp))

                PasswordOutlinedField(
                    value = uiState.newPasswordConfirm,
                    onValueChange = viewModel::updateConfirmPassword,
                    label = stringResource(R.string.common_label_confirm_password),
                    leadingIcon = appIcons.LockReset,
                    leadingIconContentDescription = stringResource(R.string.common_label_confirm_password),
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Done,
                    isError = uiState.errorMessage != null,
                )

                uiState.errorMessage?.let {
                    Text(
                        text = it.resolve(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.success) {
                    Text(
                        text = successMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::changePassword,
                enabled = !uiState.isLoading,
                modifier = Modifier.widthIn(min = 100.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.common_action_change))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!uiState.isLoading) {
                        viewModel.clear()
                        onDismiss()
                    }
                },
                enabled = !uiState.isLoading
            ) {
                Text(stringResource(R.string.common_action_dismiss))
            }
        }
    )
}

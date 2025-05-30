package sstu.grivvus.yamusic.passwordChange

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.sharp.Lock
import androidx.compose.material.icons.sharp.LockReset
import androidx.compose.material.icons.sharp.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.ui.theme.appIconsMirrored

@Composable
fun PasswordChangeDialog(
    viewModel: PasswordChangeViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            delay(1000)
            onDismiss()
            Toast.makeText(context, "Password has change", Toast.LENGTH_SHORT).show()
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
                text = "Password change",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = uiState.currentPassword,
                    onValueChange = viewModel::updateCurrentPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(
                            imageVector = appIcons.Lock,
                            contentDescription = "Current password"
                        )
                    },
                    isError = uiState.errorMessage != null
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.newPassword,
                    onValueChange = viewModel::updateNewPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(
                            imageVector = appIcons.Password,
                            contentDescription = "New password",
                        )
                    },
                    isError = uiState.errorMessage != null
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.newPasswordConfirm,
                    onValueChange = viewModel::updateConfirmPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password")},
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(
                            imageVector = appIcons.LockReset,
                            contentDescription = "Confirm password",
                        )
                    },
                    isError = uiState.errorMessage != null
                )

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.success) {
                    Text(
                        text = "Password has change",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.changePassword()
                    viewModel.updateSuccessFlag(uiState.errorMessage == null)
                },
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
                    Text("Change")
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
                Text("Dismiss")
            }
        }
    )
}
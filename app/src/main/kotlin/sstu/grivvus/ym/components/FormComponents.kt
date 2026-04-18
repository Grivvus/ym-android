package sstu.grivvus.ym.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CenteredFormScreen(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
    verticalSpacing: Dp = 16.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            overlay()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = content,
            )
        }
    }
}

@Composable
fun PasswordOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    leadingIconContentDescription: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = leadingIconContentDescription,
            )
        },
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = if (isVisible) "Hide password" else "Show password",
                )
            }
        },
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = KeyboardType.Password,
        ),
    )
}

@Composable
fun FormActionRow(
    primaryButtonLabel: String,
    onPrimaryButtonClick: () -> Unit,
    secondaryButtonLabel: String,
    onSecondaryButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryButtonEnabled: Boolean = true,
    secondaryButtonEnabled: Boolean = true,
    isPrimaryButtonLoading: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onSecondaryButtonClick,
            modifier = Modifier.weight(1f),
            enabled = secondaryButtonEnabled,
        ) {
            Text(secondaryButtonLabel)
        }

        Button(
            onClick = onPrimaryButtonClick,
            modifier = Modifier.weight(1f),
            enabled = primaryButtonEnabled,
        ) {
            if (isPrimaryButtonLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(primaryButtonLabel)
            }
        }
    }
}

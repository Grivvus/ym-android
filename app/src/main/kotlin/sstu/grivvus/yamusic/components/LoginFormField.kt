package sstu.grivvus.yamusic.components

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun LoginFormField(
    leadingIcon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String = "",
    isPasswordField: Boolean = false,
) {
    val visualTransformation: VisualTransformation = if (isPasswordField)
        PasswordVisualTransformation() else VisualTransformation.None
    TextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = { Icon(leadingIcon, null) },
        placeholder = { Text(placeholderText) },
        visualTransformation = visualTransformation,
    )
}
package sstu.grivvus.ym.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sstu.grivvus.ym.R

private const val DefaultErrorTooltipDurationMillis = 4_000L

enum class FeedbackTooltipTone {
    Error,
    Success,
}

@Composable
fun FeedbackTooltip(
    message: String,
    visible: Boolean,
    tone: FeedbackTooltipTone,
    modifier: Modifier = Modifier,
    durationMillis: Long = DefaultErrorTooltipDurationMillis,
    onDismiss: () -> Unit = {},
) {
    val shown = visible && message.isNotBlank()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (tone) {
        FeedbackTooltipTone.Error -> colorScheme.errorContainer
        FeedbackTooltipTone.Success -> colorScheme.primaryContainer
    }
    val contentColor = when (tone) {
        FeedbackTooltipTone.Error -> colorScheme.onErrorContainer
        FeedbackTooltipTone.Success -> colorScheme.onPrimaryContainer
    }
    val iconTint = when (tone) {
        FeedbackTooltipTone.Error -> colorScheme.error
        FeedbackTooltipTone.Success -> colorScheme.primary
    }
    val icon = when (tone) {
        FeedbackTooltipTone.Error -> Icons.Default.Error
        FeedbackTooltipTone.Success -> Icons.Default.CheckCircle
    }
    val iconDescription = when (tone) {
        FeedbackTooltipTone.Error -> R.string.common_cd_error
        FeedbackTooltipTone.Success -> R.string.common_cd_success
    }

    if (shown) {
        LaunchedEffect(message, durationMillis) {
            delay(durationMillis)
            currentOnDismiss()
        }
    }

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = containerColor,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 12.dp,
                        end = 8.dp,
                        bottom = 12.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(iconDescription),
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = currentOnDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_cd_dismiss),
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorTooltip(
    message: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Long = DefaultErrorTooltipDurationMillis,
    onDismiss: () -> Unit = {},
) {
    FeedbackTooltip(
        message = message,
        visible = visible,
        tone = FeedbackTooltipTone.Error,
        modifier = modifier,
        durationMillis = durationMillis,
        onDismiss = onDismiss,
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewErrorTooltip() {
    ErrorTooltip(
        message = "This is an error message",
        visible = true,
    )
}

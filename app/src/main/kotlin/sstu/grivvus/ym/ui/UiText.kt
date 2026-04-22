package sstu.grivvus.ym.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

sealed interface UiText {
    data class DynamicString(
        val value: String,
    ) : UiText

    data class StringResource(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class PluralResource(
        @param:PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = listOf(quantity),
    ) : UiText

    data class Joined(
        val parts: List<UiText>,
        val separator: String = " • ",
    ) : UiText
}

fun UiText.resolve(context: Context): String =
    when (this) {
        is UiText.DynamicString -> value
        is UiText.StringResource -> context.getString(id, *args.resolveArguments(context))
        is UiText.PluralResource -> context.resources.getQuantityString(
            id,
            quantity,
            *args.resolveArguments(context),
        )
        is UiText.Joined -> parts.joinToString(separator) { part -> part.resolve(context) }
    }

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)

fun String.asUiText(): UiText = UiText.DynamicString(this)

fun String?.asUiTextOrNull(): UiText? = this?.takeIf { it.isNotBlank() }?.asUiText()

private fun List<Any>.resolveArguments(context: Context): Array<Any> =
    map { value ->
        if (value is UiText) {
            value.resolve(context)
        } else {
            value
        }
    }.toTypedArray()

package com.example.plantry.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Lets the keyboard start a field with a capital letter, as [KeyboardCapitalization][androidx.compose.ui.text.input.KeyboardCapitalization]
 * asks. Keyboards such as Gboard take the shift state of the first letter from
 * `EditorInfo.initialCapsMode`, which Compose leaves at 0 (a platform `TextView` fills it in), so
 * an empty field opened in lowercase.
 */
@Composable
fun CapitalizeOnFocus(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    request.createInputConnection(outAttributes).also { connection ->
                        outAttributes.initialCapsMode = connection.getCursorCapsMode(outAttributes.inputType)
                    }
                },
            )
        },
        content = content,
    )
}

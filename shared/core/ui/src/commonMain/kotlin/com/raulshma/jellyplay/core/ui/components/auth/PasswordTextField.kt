package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_hide_password
import com.raulshma.jellyplay.core.ui.generated.resources.core_show_password

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Shape
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff

/**
 * Shared masked text field with optional visibility toggle (eye icon).
 *
 * Replaces the duplicated inline `OutlinedTextField` + `PasswordVisualTransformation`
 * + trailing eye-icon pattern that was copy-pasted across auth, admin, onboarding,
 * and settings screens. Call sites that need PIN-style entry (no toggle) pass
 * `showVisibilityToggle = false` and `keyboardType = KeyboardType.NumberPassword`.
 *
 * @param contentType When non-null, applied via `Modifier.semantics { contentType = ... }`
 *   so autofill frameworks classify the field correctly.
 */
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Password,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentType: ContentType? = null,
    showVisibilityToggle: Boolean = true,
    shape: Shape = OutlinedTextFieldDefaults.shape,
) {
    var visible by remember { mutableStateOf(false) }

    val semanticsModifier = if (contentType != null) {
        modifier.semantics { this.contentType = contentType }
    } else {
        modifier
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = semanticsModifier,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        shape = shape,
        trailingIcon = if (showVisibilityToggle) {
            {
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.focusIndicator(CircleShape),
                ) {
                    Icon(
                        imageVector = if (visible) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                        contentDescription = stringResource(
                            if (visible) Res.string.core_hide_password else Res.string.core_show_password,
                        ),
                    )
                }
            }
        } else {
            null
        },
    )
}

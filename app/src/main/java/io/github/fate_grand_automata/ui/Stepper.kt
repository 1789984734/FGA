package io.github.fate_grand_automata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.fate_grand_automata.ui.prefs.StatusWrapper

@Composable
private fun DeltaButton(
    currentValue: Int,
    onCurrentValueChange: (Int) -> Unit,
    onCommit: () -> Unit,
    valueRange: IntRange,
    delta: Int,
    text: String,
    enabled: Boolean
) {
    val canDelta = (currentValue + delta) in valueRange
    val isEnabled = enabled && canDelta

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = CircleShape
    ) {
        StatusWrapper(enabled = isEnabled) {
            Text(
                text,
                modifier = Modifier
                    .holdRepeatClickable(
                        onRepeat = {
                            onCurrentValueChange((currentValue + delta).coerceIn(valueRange))
                        },
                        onEnd = onCommit,
                        enabled = isEnabled
                    )
                    .padding(20.dp, 10.dp)
            )
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    enabled: Boolean = true,
    delta: Int = 1,
    valueRepresentation: (Int) -> String = { it.toString() }
) {
    var currentValue by remember(value) { mutableStateOf(value) }
    var isEditing by remember { mutableStateOf(false) }

    val onCommit = { onValueChange(currentValue.coerceIn(valueRange)) }
    val leaveEditor = { isEditing = false }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeltaButton(
            currentValue = currentValue,
            onCurrentValueChange = {
                leaveEditor()
                currentValue = it
            },
            onCommit = onCommit,
            valueRange = valueRange,
            delta = -delta,
            text = "-",
            enabled = enabled
        )

        if (isEditing) {
            StepperValueEditor(
                initial = currentValue,
                valueRange = valueRange,
                onSubmit = { parsed ->
                    currentValue = parsed
                    onCommit()
                    isEditing = false
                },
                onCancel = { isEditing = false }
            )
        } else {
            StatusWrapper(enabled = enabled) {
                Text(
                    valueRepresentation(currentValue),
                    textAlign = TextAlign.Center,
                    textDecoration = if (enabled) TextDecoration.Underline else null,
                    modifier = Modifier
                        .widthIn(min = 36.dp)
                        .clickable(enabled = enabled) { isEditing = true }
                        .padding(horizontal = 5.dp)
                )
            }
        }

        DeltaButton(
            currentValue = currentValue,
            onCurrentValueChange = {
                leaveEditor()
                currentValue = it
            },
            onCommit = onCommit,
            valueRange = valueRange,
            delta = delta,
            text = "+",
            enabled = enabled
        )
    }
}

@Composable
private fun StepperValueEditor(
    initial: Int,
    valueRange: IntRange,
    onSubmit: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initial.toString(),
                selection = TextRange(initial.toString().length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }

    fun submit() {
        val parsed = fieldValue.text.trim().toIntOrNull()
        if (parsed != null) {
            onSubmit(parsed.coerceIn(valueRange))
        } else {
            onCancel()
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { next ->
            // digits only; keep empty so the user can clear before typing
            val filtered = next.text.filter { it.isDigit() }
            fieldValue = next.copy(
                text = filtered,
                selection = TextRange(
                    (next.selection.end.coerceIn(0, filtered.length))
                )
            )
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .widthIn(min = 36.dp)
            .defaultMinSize(minWidth = 36.dp)
            .padding(horizontal = 5.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    // tap outside the field: keep a valid typed value, otherwise restore
                    submit()
                }
            }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

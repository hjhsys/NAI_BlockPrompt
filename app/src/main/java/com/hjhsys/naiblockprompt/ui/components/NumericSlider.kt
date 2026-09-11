package com.hjhsys.naiblockprompt.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** A discrete numeric range whose Compose slider position is always an integer tick. */
class NumericSliderSpec(
    val min: Double,
    val max: Double,
    val step: Double,
    val displayDecimals: Int,
) {
    private val factor = 10.0.pow(
        maxOf(displayDecimals, decimalPlaces(min), decimalPlaces(max), decimalPlaces(step)),
    )
    private val minUnits = (min * factor).roundToInt()
    private val maxUnits = (max * factor).roundToInt()
    private val stepUnits = (step * factor).roundToInt()
    val lastTickIndex: Int

    init {
        require(min < max)
        require(step > 0.0)
        require(displayDecimals >= 0)
        require(stepUnits > 0)
        require((maxUnits - minUnits) % stepUnits == 0) { "Range must contain a whole number of steps" }
        lastTickIndex = (maxUnits - minUnits) / stepUnits
    }

    val composeSteps: Int get() = (lastTickIndex - 1).coerceAtLeast(0)

    fun tickIndex(value: Double): Int =
        (((value * factor).roundToInt() - minUnits).toDouble() / stepUnits).roundToInt()
            .coerceIn(0, lastTickIndex)

    fun valueAtTick(index: Int): Double =
        (minUnits + index.coerceIn(0, lastTickIndex) * stepUnits) / factor

    fun canonicalValue(value: Double): Double = valueAtTick(tickIndex(value))

    fun format(value: Double): String =
        String.format(Locale.US, "%.${displayDecimals}f", canonicalValue(value))

    fun formatExact(value: Double): String =
        String.format(Locale.US, "%.${displayDecimals}f", value)

    /** Parses only finite, in-range values that lie exactly on this spec's tick grid. */
    fun parseInput(text: String): Double? {
        val parsed = text.trim().toBigDecimalOrNull() ?: return null
        val scaled = runCatching { parsed.movePointRight(decimalScale).toBigIntegerExact().intValueExact() }.getOrNull()
            ?: return null
        if (scaled !in minUnits..maxUnits) return null
        if ((scaled - minUnits) % stepUnits != 0) return null
        return scaled / factor
    }

    private val decimalScale = maxOf(displayDecimals, decimalPlaces(min), decimalPlaces(max), decimalPlaces(step))

    private companion object {
        fun decimalPlaces(value: Double): Int =
            BigDecimal.valueOf(value).stripTrailingZeros().scale().coerceAtLeast(0)

    }
}

@Composable
fun NumericSlider(
    value: Double,
    spec: NumericSliderSpec,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Slider(
        value = spec.tickIndex(value).toFloat(),
        onValueChange = { onValueChange(spec.valueAtTick(it.roundToInt())) },
        valueRange = 0f..spec.lastTickIndex.toFloat(),
        steps = spec.composeSteps,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun NumericValueEditor(
    value: Double,
    spec: NumericSliderSpec,
    label: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var receivedFocus by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun close(commit: Boolean) {
        if (closing) return
        closing = true
        if (commit) spec.parseInput(input.text)?.let(onValueChange)
        editing = false
        focusManager.clearFocus()
    }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    if (!editing) {
        Surface(
            onClick = {
                val formatted = spec.format(value)
                input = TextFieldValue(formatted, selection = TextRange(0, formatted.length))
                receivedFocus = false
                closing = false
                editing = true
            },
            modifier = modifier.semantics { contentDescription = label },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(spec.format(value), Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        val valid = spec.parseInput(input.text) != null
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(
                1.dp,
                if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            ),
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .widthIn(min = 72.dp, max = 112.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) receivedFocus = true
                        else if (receivedFocus && !closing) close(commit = true)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                            close(commit = false)
                            true
                        } else false
                    }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (valid) close(commit = true)
                }),
            )
        }
    }
}

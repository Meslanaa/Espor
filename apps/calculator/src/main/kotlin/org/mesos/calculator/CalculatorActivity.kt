package org.mesos.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Calculator. */
class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MesOSUserTheme { CalculatorScreen() }
        }
    }
}

private enum class KeyKind { DIGIT, OPERATOR, FUNCTION, EQUALS }

private data class Key(val label: String, val kind: KeyKind)

private val keypad = listOf(
    listOf(Key("C", KeyKind.FUNCTION), Key("⌫", KeyKind.FUNCTION), Key("%", KeyKind.FUNCTION), Key("÷", KeyKind.OPERATOR)),
    listOf(Key("7", KeyKind.DIGIT), Key("8", KeyKind.DIGIT), Key("9", KeyKind.DIGIT), Key("×", KeyKind.OPERATOR)),
    listOf(Key("4", KeyKind.DIGIT), Key("5", KeyKind.DIGIT), Key("6", KeyKind.DIGIT), Key("−", KeyKind.OPERATOR)),
    listOf(Key("1", KeyKind.DIGIT), Key("2", KeyKind.DIGIT), Key("3", KeyKind.DIGIT), Key("+", KeyKind.OPERATOR)),
    listOf(Key("( )", KeyKind.FUNCTION), Key("0", KeyKind.DIGIT), Key(".", KeyKind.DIGIT), Key("=", KeyKind.EQUALS)),
)

@Composable
private fun CalculatorScreen() {
    var expression by rememberSaveable { mutableStateOf("") }
    // After "=", typing a digit starts a new calculation instead of appending.
    var showingResult by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<CalcError?>(null) }

    val preview = if (!showingResult && CalculatorInput.hasOperation(expression)) {
        (CalculatorEngine.evaluate(expression) as? CalcResult.Value)?.let { CalculatorEngine.format(it.value) }
    } else {
        null
    }

    fun press(key: Key) {
        error = null
        val base = if (showingResult && key.kind == KeyKind.DIGIT) "" else expression
        showingResult = false
        expression = when (key.label) {
            "C" -> ""
            "⌫" -> CalculatorInput.backspace(base)
            "%" -> CalculatorInput.percent(base)
            "( )" -> CalculatorInput.parenthesis(base)
            "." -> CalculatorInput.decimal(base)
            "=" -> when (val result = CalculatorEngine.evaluate(base)) {
                is CalcResult.Value -> {
                    showingResult = true
                    CalculatorEngine.toExpression(result.value)
                }
                is CalcResult.Error -> {
                    error = result.error
                    base
                }
            }
            else -> if (key.kind == KeyKind.OPERATOR) {
                CalculatorInput.operator(base, key.label[0])
            } else {
                CalculatorInput.digit(base, key.label[0])
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = expression.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = when {
                        expression.length > 18 -> 28.sp
                        expression.length > 12 -> 36.sp
                        else -> 48.sp
                    },
                    textAlign = TextAlign.End,
                    maxLines = 3,
                )
                val message = error?.let { stringResource(errorText(it)) }
                Text(
                    text = message ?: preview.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            keypad.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        KeyButton(key, Modifier.weight(1f)) { press(key) }
                    }
                }
            }
        }
    }
}

private fun errorText(error: CalcError): Int = when (error) {
    CalcError.SYNTAX -> R.string.calculator_error_syntax
    CalcError.DIVIDE_BY_ZERO -> R.string.calculator_error_divide_by_zero
    CalcError.OVERFLOW -> R.string.calculator_error_overflow
}

@Composable
private fun KeyButton(key: Key, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (key.kind) {
        KeyKind.DIGIT -> colors.surfaceContainerHigh to colors.onSurface
        KeyKind.OPERATOR -> colors.primaryContainer to colors.onPrimaryContainer
        KeyKind.FUNCTION -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyKind.EQUALS -> colors.primary to colors.onPrimary
    }
    val description = when (key.label) {
        "⌫" -> stringResource(R.string.calculator_backspace)
        "C" -> stringResource(R.string.calculator_clear)
        "( )" -> stringResource(R.string.calculator_parentheses)
        else -> key.label
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.headlineSmall,
            color = foreground,
            modifier = Modifier.padding(vertical = 18.dp),
        )
    }
}

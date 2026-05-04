package com.konhit.financeapp.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class CalcState(
    val display: String = "0",
    val pendingOp: Char? = null,
    val operand: Double? = null,
    val clearNext: Boolean = false
)

@Composable
fun InlineCalculator(
    onResult: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var calc by remember { mutableStateOf(CalcState()) }

    fun handleDigit(d: String) {
        calc = if (calc.clearNext) {
            calc.copy(display = d, clearNext = false)
        } else {
            calc.copy(display = if (calc.display == "0") d else calc.display + d)
        }
    }

    fun handleOp(op: Char) {
        val state = calc
        val current = state.display.toDoubleOrNull() ?: return
        val result = if (state.pendingOp != null && state.operand != null) {
            applyOp(state.operand, current, state.pendingOp)
        } else current
        calc = CalcState(display = result.toDisplayString(), pendingOp = op, operand = result, clearNext = true)
    }

    fun handleEquals() {
        val state = calc
        val current = state.display.toDoubleOrNull() ?: return
        val result = if (state.pendingOp != null && state.operand != null) {
            applyOp(state.operand, current, state.pendingOp)
        } else current
        onResult(result)
    }

    fun handleDot() {
        if (!calc.display.contains('.')) {
            calc = calc.copy(display = calc.display + ".")
        }
    }

    fun handleBackspace() {
        calc = calc.copy(
            display = if (calc.display.length <= 1) "0" else calc.display.dropLast(1)
        )
    }

    Column {
        Text(
            text = calc.display,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val buttons = listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "-"),
            listOf("C", "0", ".", "+"),
            listOf("⌫", "=")
        )
        buttons.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    val weight = if (label == "=") 2f else 1f
                    FilledTonalButton(
                        onClick = {
                            when (label) {
                                "C"  -> { calc = CalcState(); }
                                "⌫" -> handleBackspace()
                                "+"  -> handleOp('+')
                                "-"  -> handleOp('-')
                                "×"  -> handleOp('×')
                                "÷"  -> handleOp('÷')
                                "="  -> handleEquals()
                                "."  -> handleDot()
                                else -> handleDigit(label)
                            }
                        },
                        modifier = Modifier.weight(weight).padding(2.dp)
                    ) { Text(label) }
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun applyOp(a: Double, b: Double, op: Char?): Double = when (op) {
    '+' -> a + b
    '-' -> a - b
    '×' -> a * b
    '÷' -> if (b != 0.0) a / b else a
    else -> b
}

private fun Double.toDisplayString(): String =
    if (this == kotlin.math.floor(this) && !this.isInfinite()) toLong().toString()
    else toString()

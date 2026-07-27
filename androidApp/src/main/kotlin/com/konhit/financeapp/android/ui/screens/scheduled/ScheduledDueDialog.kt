package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.konhit.financeapp.android.ui.util.formatAmount
import com.konhit.financeapp.domain.model.TransactionType

@Composable
fun ScheduledDueDialog(
    info: DueDialogInfo,
    busy: Boolean,
    onApprove: () -> Unit,
    onCancelOnce: () -> Unit,
    onDeleteScheduled: () -> Unit
) {
    val s = info.scheduled
    val amountColor = when (s.type) {
        TransactionType.DEPOSIT -> Color(0xFF2E7D32)
        TransactionType.WITHDRAWAL -> Color(0xFFC62828)
        TransactionType.TRANSFER -> Color(0xFF1565C0)
    }
    val amountLine = when (s.type) {
        TransactionType.TRANSFER ->
            "${formatAmount(s.transAmount, null)} → ${formatAmount(s.toTransAmount, null)}"
        else -> formatAmount(s.transAmount, null)
    }
    val transferLine = if (s.type == TransactionType.TRANSFER) {
        "\nTo: ${info.toAccountName ?: ""}"
    } else ""
    val notesLine = s.notes?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""

    AlertDialog(
        onDismissRequest = { /* must choose an action */ },
        title = { Text("Scheduled transaction due") },
        text = {
            Text(
                buildString {
                    append(s.nextOccurrenceDate.substringBefore('T'))
                    append("\n")
                    append(s.type.mmexCode)
                    append(" · ")
                    append(info.accountName)
                    append(transferLine)
                    append("\n")
                    append(info.categoryPath.ifBlank { "—" })
                    append("\n")
                    append(amountLine)
                    append(notesLine)
                },
                color = amountColor
            )
        },
        confirmButton = {
            TextButton(onClick = onApprove, enabled = !busy) { Text("Approve") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onCancelOnce, enabled = !busy) { Text("Cancel once") }
                TextButton(onClick = onDeleteScheduled, enabled = !busy) { Text("Delete") }
            }
        }
    )
}

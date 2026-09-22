package com.konhit.financeapp.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Currency

/**
 * "Base currency" dropdown (with a None entry) plus the exchange-rate loading
 * progress, or the load error with a Retry button, underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseCurrencySelector(
    currencies: List<Currency>,
    selectedCurrencyId: Long?,
    rateProgress: Int?,
    rateError: String?,
    onSelect: (Long?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label: (Currency) -> String = { c -> c.name + (c.currencySymbol?.let { " ($it)" } ?: "") }
    val selectedLabel = currencies.find { it.id == selectedCurrencyId }?.let(label) ?: "None"

    Column(modifier) {
        Text("Base currency", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = { onSelect(null); expanded = false }
                )
                currencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(label(currency)) },
                        onClick = { onSelect(currency.id); expanded = false }
                    )
                }
            }
        }
        if (rateProgress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rateProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Loading exchange rates… $rateProgress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (rateError != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

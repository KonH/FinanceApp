package com.konhit.financeapp.android.ui.screens.currencies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Currency
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesScreen(onBack: () -> Unit) {
    val viewModel: CurrenciesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currencies") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Default.Add, "Add currency")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.currencies, key = { it.id }) { currency ->
                CurrencyItem(
                    currency = currency,
                    onEdit = { viewModel.onEditClick(currency) },
                    onDelete = { viewModel.onDelete(currency.id) }
                )
                HorizontalDivider()
            }
        }
    }

    state.dialog?.let { dialog ->
        CurrencyDialog(
            dialog = dialog,
            onNameChange = viewModel::onDialogNameChange,
            onSymbolChange = viewModel::onDialogSymbolChange,
            onPfxChange = viewModel::onDialogPfxChange,
            onDismiss = viewModel::onDialogDismiss,
            onSave = viewModel::onDialogSave
        )
    }
}

@Composable
private fun CurrencyItem(
    currency: Currency,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(currency.name) },
        supportingContent = {
            val symbol = currency.currencySymbol ?: currency.pfxSymbol ?: currency.sfxSymbol
            if (symbol != null) Text(symbol)
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    )
}

@Composable
private fun CurrencyDialog(
    dialog: CurrencyDialogState,
    onNameChange: (String) -> Unit,
    onSymbolChange: (String) -> Unit,
    onPfxChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog.id == null) "Add Currency" else "Edit Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dialog.symbol,
                    onValueChange = onSymbolChange,
                    label = { Text("Symbol (e.g. €)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dialog.pfxSymbol,
                    onValueChange = onPfxChange,
                    label = { Text("Prefix (e.g. USD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = dialog.name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

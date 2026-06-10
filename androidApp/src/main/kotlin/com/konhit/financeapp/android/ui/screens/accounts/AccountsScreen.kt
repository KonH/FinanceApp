package com.konhit.financeapp.android.ui.screens.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Currency
import org.koin.androidx.compose.koinViewModel

private val accountTypes = listOf("Cash", "Savings", "Investment")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val viewModel: AccountsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Default.Add, "Add account")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.accounts, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(account.type) },
                    trailingContent = {
                        Row {
                            val hidden = account.id in state.hiddenAccountIds
                            IconButton(onClick = { viewModel.onToggleVisibility(account.id) }) {
                                Icon(
                                    if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (hidden) "Show on main screen" else "Hide from main screen"
                                )
                            }
                            IconButton(onClick = { viewModel.onEditClick(account) }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { viewModel.onDelete(account.id) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    state.dialog?.let { dialog ->
        AccountDialog(
            dialog = dialog,
            currencies = state.currencies,
            onNameChange = viewModel::onDialogNameChange,
            onTypeChange = viewModel::onDialogTypeChange,
            onInitialBalChange = viewModel::onDialogInitialBalChange,
            onCurrencyChange = viewModel::onDialogCurrencyChange,
            onDismiss = viewModel::onDialogDismiss,
            onSave = viewModel::onDialogSave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialog(
    dialog: AccountDialogState,
    currencies: List<Currency>,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onInitialBalChange: (String) -> Unit,
    onCurrencyChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog.id == null) "Add Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = dialog.type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        accountTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { onTypeChange(type); typeExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dialog.initialBal,
                    onValueChange = onInitialBalChange,
                    label = { Text("Initial balance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currencies.isNotEmpty()) {
                    val selectedCurrency = currencies.find { it.id == dialog.currencyId }
                    ExposedDropdownMenuBox(
                        expanded = currencyExpanded,
                        onExpandedChange = { currencyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Currency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            currencies.forEach { currency ->
                                DropdownMenuItem(
                                    text = { Text(currency.name) },
                                    onClick = { onCurrencyChange(currency.id); currencyExpanded = false }
                                )
                            }
                        }
                    }
                }
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

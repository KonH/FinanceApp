package com.konhit.financeapp.android.ui.screens.account

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.util.formatAmount
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountId: Long,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: AccountViewModel = koinViewModel(parameters = { parametersOf(accountId) })
    val state by viewModel.state.collectAsState()
    var transactionToDelete by remember { mutableStateOf<Long?>(null) }

    transactionToDelete?.let { transId ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteTransaction(transId)
                    transactionToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    val accountCurrency = state.account?.let { state.accountCurrencies[it.currencyId] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.account?.name ?: "")
                        Text(
                            formatAmount(state.account?.balance ?: 0.0, accountCurrency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isReadOnly) {
                FloatingActionButton(onClick = onAddTransaction) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.transactions, key = { it.transId }) { tx ->
                TransactionRow(
                    transaction = tx,
                    categories = state.categories,
                    accountCurrencies = state.accountCurrencies,
                    isReadOnly = state.isReadOnly,
                    onEdit = { onEditTransaction(tx.transId) },
                    onDelete = { transactionToDelete = tx.transId }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    categories: Map<Long, String>,
    accountCurrencies: Map<Long, Currency>,
    isReadOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(categories[transaction.categId] ?: transaction.notes ?: "")
        },
        supportingContent = { Text(transaction.transDate.substringBefore('T')) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val amountColor = when (transaction.type) {
                    TransactionType.DEPOSIT    -> Color(0xFF2E7D32)
                    TransactionType.WITHDRAWAL -> Color(0xFFC62828)
                    TransactionType.TRANSFER   -> Color(0xFF1565C0)
                }
                if (transaction.type == TransactionType.TRANSFER) {
                    val fromCurrency = accountCurrencies[transaction.accountId]
                    val toCurrency = accountCurrencies[transaction.toAccountId]
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(formatAmount(transaction.transAmount, fromCurrency), color = amountColor)
                        Text(
                            "→ ${formatAmount(transaction.toTransAmount, toCurrency)}",
                            color = amountColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    val currency = accountCurrencies[transaction.accountId]
                    Text(
                        formatAmount(transaction.transAmount, currency),
                        color = amountColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                if (!isReadOnly) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    )
}

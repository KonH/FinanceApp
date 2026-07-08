package com.konhit.financeapp.android.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.util.formatAmount
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.SyncState
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAccountClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    val viewModel: MainViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var budgetExpanded by remember { mutableStateOf(false) }

    val syncState = state.syncState
    if (syncState is SyncState.Conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Sync conflict") },
            text = { Text("The remote file has been modified since your last sync. Which version do you want to keep?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onConflictKeepRemote() }) { Text("Use remote") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onConflictKeepLocal() }) { Text("Keep local") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                actions = {
                    IconButton(onClick = { viewModel.onToggleBalanceVisibility() }) {
                        Icon(
                            if (state.balanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (state.balanceVisible) "Hide balances" else "Show balances"
                        )
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter transactions")
                    }
                    IconButton(onClick = { viewModel.onSyncClick() }) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = if (syncState is SyncState.PendingSync)
                                Color(0xFFF59E0B)
                            else
                                LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AccountGroupList(
                groups = state.accountGroups,
                currencies = state.currencies,
                balanceVisible = state.balanceVisible,
                onAccountClick = onAccountClick,
                modifier = Modifier.weight(1f)
            )

            TotalBalanceSection(
                totals = state.totalsByCurrency,
                balanceVisible = state.balanceVisible
            )

            if (state.budgetItems.isNotEmpty()) {
                FilterChip(
                    selected = budgetExpanded,
                    onClick = { budgetExpanded = !budgetExpanded },
                    label = { Text("Budget") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (budgetExpanded) {
                    BudgetSection(state.budgetItems)
                }
            }

            if (syncState is SyncState.Error) {
                Text(
                    "Sync error: ${syncState.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountGroupList(
    groups: List<AccountGroup>,
    currencies: Map<Long, Currency>,
    balanceVisible: Boolean,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            item(key = "header_${group.type}") {
                ListItem(
                    headlineContent = {
                        Text(group.type, style = MaterialTheme.typography.titleSmall)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            items(group.accounts, key = { it.id }) { account ->
                AccountRow(
                    account = account,
                    currency = currencies[account.currencyId],
                    balanceVisible = balanceVisible,
                    onClick = { onAccountClick(account.id) }
                )
                HorizontalDivider()
            }
            item(key = "total_${group.type}") {
                GroupTotalSection(group.totalsByCurrency, balanceVisible)
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    currency: Currency?,
    balanceVisible: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(account.name) },
        trailingContent = {
            Text(if (balanceVisible) formatAmount(account.balance, currency) else "•••")
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun GroupTotalSection(totals: List<Pair<Currency, Double>>, balanceVisible: Boolean) {
    if (totals.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        totals.forEach { (currency, total) ->
            Text(
                if (balanceVisible) formatAmount(total, currency) else "•••",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun TotalBalanceSection(totals: List<Pair<Currency, Double>>, balanceVisible: Boolean) {
    if (totals.isEmpty()) return
    HorizontalDivider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Total", style = MaterialTheme.typography.labelMedium)
        totals.forEach { (currency, total) ->
            Text(
                if (balanceVisible) formatAmount(total, currency) else "•••",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BudgetSection(items: List<BudgetItem>) {
    HorizontalDivider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        items.forEach { item ->
            val fraction = (item.actualPercent / 100.0).toFloat().coerceIn(0f, 1f)
            val label = item.currency.currencySymbol?.takeIf { it.isNotEmpty() } ?: item.currency.name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "$label ${item.actualPercent.roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (item.showExpected)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                if (item.showExpected) {
                    Text(
                        " (expected ${item.expectedPercent.roundToInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

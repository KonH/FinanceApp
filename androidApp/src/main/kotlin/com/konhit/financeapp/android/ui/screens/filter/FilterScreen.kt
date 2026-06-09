package com.konhit.financeapp.android.ui.screens.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.components.CategoryPickerDialog
import com.konhit.financeapp.android.ui.util.formatAmount
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.model.buildPath
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(onBack: () -> Unit) {
    val viewModel: FilterViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        viewModel.onFilterChanged(state.filter.copy(startDate = date.toString()))
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        viewModel.onFilterChanged(state.filter.copy(endDate = date.toString()))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                FilterSectionHeader(
                    expanded = state.isFiltersExpanded,
                    onToggle = { viewModel.onToggleFiltersExpanded() }
                )
            }
            item {
                AnimatedVisibility(visible = state.isFiltersExpanded) {
                    FilterOptionsPanel(
                        filter = state.filter,
                        accounts = state.accounts,
                        currencies = state.currencies,
                        categoryList = state.categoryList,
                        onFilterChanged = { viewModel.onFilterChanged(it) },
                        onShowStartDatePicker = { showStartDatePicker = true },
                        onShowEndDatePicker = { showEndDatePicker = true }
                    )
                }
            }
            item {
                HorizontalDivider()
                Text(
                    "${state.transactions.size} transactions",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(state.transactions, key = { it.transId }) { tx ->
                FilterTransactionRow(
                    transaction = tx,
                    categories = state.categories,
                    accountCurrenciesMap = state.accountCurrenciesMap,
                    accounts = state.accounts
                )
                HorizontalDivider()
            }
            item {
                FilterBalanceSummary(balanceSums = state.balanceSums)
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text("Filters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
    }
}

@Composable
private fun FilterOptionsPanel(
    filter: TransactionFilter,
    accounts: List<Account>,
    currencies: List<Currency>,
    categoryList: List<Category>,
    onFilterChanged: (TransactionFilter) -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowEndDatePicker: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Type", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filter.type == null,
                    onClick = { onFilterChanged(filter.copy(type = null)) },
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = filter.type == TransactionType.DEPOSIT,
                    onClick = {
                        onFilterChanged(
                            filter.copy(type = if (filter.type == TransactionType.DEPOSIT) null else TransactionType.DEPOSIT)
                        )
                    },
                    label = { Text("Deposit") }
                )
            }
            item {
                FilterChip(
                    selected = filter.type == TransactionType.WITHDRAWAL,
                    onClick = {
                        onFilterChanged(
                            filter.copy(type = if (filter.type == TransactionType.WITHDRAWAL) null else TransactionType.WITHDRAWAL)
                        )
                    },
                    label = { Text("Withdrawal") }
                )
            }
            item {
                FilterChip(
                    selected = filter.type == TransactionType.TRANSFER,
                    onClick = {
                        onFilterChanged(
                            filter.copy(type = if (filter.type == TransactionType.TRANSFER) null else TransactionType.TRANSFER)
                        )
                    },
                    label = { Text("Transfer") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Account", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        AccountDropdown(
            accounts = accounts,
            selectedAccountId = filter.accountId,
            onSelect = { onFilterChanged(filter.copy(accountId = it)) }
        )

        Spacer(Modifier.height(12.dp))
        Text("Currency", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        CurrencyDropdown(
            currencies = currencies,
            selectedCurrencyId = filter.currencyId,
            onSelect = { onFilterChanged(filter.copy(currencyId = it)) }
        )

        Spacer(Modifier.height(12.dp))
        Text("Category", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        CategoryTreePicker(
            categories = categoryList,
            selectedCategoryId = filter.categoryId,
            onSelect = { onFilterChanged(filter.copy(categoryId = it)) }
        )

        Spacer(Modifier.height(12.dp))
        Text("Date range", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(
                label = "From",
                date = filter.startDate,
                onPick = onShowStartDatePicker,
                onClear = { onFilterChanged(filter.copy(startDate = null)) },
                modifier = Modifier.weight(1f)
            )
            DateField(
                label = "To",
                date = filter.endDate,
                onPick = onShowEndDatePicker,
                onClear = { onFilterChanged(filter.copy(endDate = null)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Amount range", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filter.minAmount?.toString() ?: "",
                onValueChange = { onFilterChanged(filter.copy(minAmount = it.toDoubleOrNull())) },
                label = { Text("Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = filter.maxAmount?.toString() ?: "",
                onValueChange = { onFilterChanged(filter.copy(maxAmount = it.toDoubleOrNull())) },
                label = { Text("Max") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DateField(
    label: String,
    date: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.weight(1f)
        ) {
            Text(date ?: label, maxLines = 1)
        }
        if (date != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = accounts.find { it.id == selectedAccountId }?.name ?: "All accounts"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All accounts") },
                onClick = { onSelect(null); expanded = false }
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { onSelect(account.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    currencies: List<Currency>,
    selectedCurrencyId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = currencies.find { it.id == selectedCurrencyId }
        ?.let { it.currencySymbol ?: it.pfxSymbol ?: it.name }
        ?: "All currencies"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All currencies") },
                onClick = { onSelect(null); expanded = false }
            )
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.name + (currency.currencySymbol?.let { " ($it)" } ?: "")) },
                    onClick = { onSelect(currency.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun CategoryTreePicker(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        CategoryPickerDialog(
            categories = categories,
            onSelect = { cat -> onSelect(cat.id); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }

    val selectedLabel = selectedCategoryId
        ?.let { categories.buildPath(it).ifEmpty { null } }
        ?: "All categories"

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.weight(1f)
        ) {
            Text(selectedLabel, maxLines = 1)
        }
        if (selectedCategoryId != null) {
            IconButton(onClick = { onSelect(null) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FilterTransactionRow(
    transaction: Transaction,
    categories: Map<Long, String>,
    accountCurrenciesMap: Map<Long, Currency>,
    accounts: List<Account>
) {
    val amountColor = when (transaction.type) {
        TransactionType.DEPOSIT    -> Color(0xFF2E7D32)
        TransactionType.WITHDRAWAL -> Color(0xFFC62828)
        TransactionType.TRANSFER   -> Color(0xFF1565C0)
    }
    val accountName = accounts.find { it.id == transaction.accountId }?.name ?: ""
    val currency = accountCurrenciesMap[transaction.accountId]

    ListItem(
        headlineContent = { Text(categories[transaction.categId] ?: transaction.notes ?: "") },
        supportingContent = {
            Text("${transaction.transDate.substringBefore('T')}  ·  $accountName")
        },
        trailingContent = {
            val amountText = when (transaction.type) {
                TransactionType.TRANSFER -> {
                    val toCurrency = accountCurrenciesMap[transaction.toAccountId]
                    "${formatAmount(transaction.transAmount, currency)} → ${formatAmount(transaction.toTransAmount, toCurrency)}"
                }
                else -> formatAmount(transaction.transAmount, currency)
            }
            Text(amountText, color = amountColor)
        }
    )
}

@Composable
private fun FilterBalanceSummary(balanceSums: List<Pair<Currency, Double>>) {
    if (balanceSums.isEmpty()) return
    HorizontalDivider()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Net flow", style = MaterialTheme.typography.labelMedium)
        balanceSums.forEach { (currency, total) ->
            Text(formatAmount(total, currency), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

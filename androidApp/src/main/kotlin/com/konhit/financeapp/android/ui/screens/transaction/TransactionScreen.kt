package com.konhit.financeapp.android.ui.screens.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.components.CategoryPickerDialog
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.model.buildPath
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    initialAccountId: Long?,
    editTransId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: TransactionViewModel = koinViewModel(
        parameters = { parametersOf(initialAccountId, editTransId) }
    )
    val state by viewModel.state.collectAsState()

    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val amountFocus = remember { FocusRequester() }
    val toAmountFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    LaunchedEffect(state.error) {
        if (state.error != null) scrollState.animateScrollTo(0)
    }

    if (state.showZeroToAmountConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissZeroToAmountConfirm() },
            title = { Text("Zero destination amount") },
            text = { Text("The destination amount is 0. Save transfer anyway?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmZeroToAmount() }) { Text("Save anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissZeroToAmountConfirm() }) { Text("Cancel") }
            }
        )
    }

    if (showDatePicker) {
        val initialMillis = remember(state.transDate) {
            val datePart = state.transDate.substringBefore('T')
            runCatching {
                java.time.LocalDate.parse(datePart)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant().toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        viewModel.onDateChanged("${date}T00:00:00")
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = state.categories,
            onSelect = { cat ->
                viewModel.onCategorySelected(cat)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTransId != null) "Edit transaction" else "Add transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onSave() },
                        enabled = !state.isSaving
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            // Transaction type
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChanged(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size)
                    ) { Text(type.mmexCode) }
                }
            }

            // Account selector
            ExposedDropdownSelector(
                label = "Account",
                options = state.accounts.map { it.id to it.name },
                selectedId = state.accountId,
                onSelected = { viewModel.onAccountChanged(it) }
            )

            // To-account (transfers only)
            if (state.type == TransactionType.TRANSFER) {
                ExposedDropdownSelector(
                    label = "To account",
                    options = state.accounts.map { it.id to it.name },
                    selectedId = state.toAccountId,
                    onSelected = { viewModel.onToAccountChanged(it) }
                )
            }

            // Category
            val categoryLabel = state.categId?.let { state.categories.buildPath(it) }
                ?.takeIf { it.isNotEmpty() } ?: "Select category"
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(categoryLabel) }

            // Date
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(state.transDate.substringBefore('T')) }

            // Amount
            OutlinedTextField(
                value = state.amount,
                onValueChange = { viewModel.onAmountChanged(it) },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    if (state.type == TransactionType.TRANSFER) toAmountFocus.requestFocus()
                    else notesFocus.requestFocus()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
            )

            // To-amount (transfers only)
            if (state.type == TransactionType.TRANSFER) {
                OutlinedTextField(
                    value = state.toAmount,
                    onValueChange = { viewModel.onToAmountChanged(it) },
                    label = { Text("Destination amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(toAmountFocus)
                )
            }

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onNotesChanged(it) },
                label = { Text("Notes") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(notesFocus),
                minLines = 2
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownSelector(
    label: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedId }?.second ?: "Select…"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelected(id); expanded = false }
                )
            }
        }
    }
}

package com.konhit.financeapp.android.ui.screens.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.components.CategoryPickerDialog
import com.konhit.financeapp.android.ui.components.InlineCalculator
import com.konhit.financeapp.domain.model.TransactionType
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

    var showCalculator by remember { mutableStateOf(false) }
    var showToCalculator by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
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
        if (showCalculator) {
            InlineCalculator(
                onResult = { viewModel.onAmountChanged(it); showCalculator = false },
                onDismiss = { showCalculator = false }
            )
            return@Scaffold
        }
        if (showToCalculator) {
            InlineCalculator(
                onResult = { viewModel.onToAmountChanged(it); showToCalculator = false },
                onDismiss = { showToCalculator = false }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            val selectedCat = state.categories.find { it.id == state.categId }
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(selectedCat?.name ?: "Select category") }

            // Date
            OutlinedTextField(
                value = state.transDate.substringBefore('T'),
                onValueChange = { viewModel.onDateChanged("${it}T00:00:00") },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Amount (tapping opens calculator)
            OutlinedTextField(
                value = state.amount,
                onValueChange = {},
                label = { Text("Amount") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    .also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect {
                                if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showCalculator = true
                                }
                            }
                        }
                    }
            )

            // To-amount (transfers only)
            if (state.type == TransactionType.TRANSFER) {
                OutlinedTextField(
                    value = state.toAmount,
                    onValueChange = {},
                    label = { Text("Destination amount") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        .also { source ->
                            LaunchedEffect(source) {
                                source.interactions.collect {
                                    if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                        showToCalculator = true
                                    }
                                }
                            }
                        }
                )
            }

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onNotesChanged(it) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
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

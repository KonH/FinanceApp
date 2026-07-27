package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.components.CategoryPickerDialog
import com.konhit.financeapp.domain.model.CustomPeriod
import com.konhit.financeapp.domain.model.ScheduleMode
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.model.buildPath
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledEditScreen(
    editBdId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ScheduledEditViewModel = koinViewModel(parameters = { parametersOf(editBdId) })
    val state by viewModel.state.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showNextDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = state.categories,
            onSelect = {
                viewModel.onCategorySelected(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showNextDatePicker) {
        ScheduleDatePicker(
            initialDate = state.nextDate,
            onDismiss = { showNextDatePicker = false },
            onConfirm = {
                viewModel.onNextDateChanged("${it}T00:00:00")
                showNextDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        ScheduleDatePicker(
            initialDate = state.endDate ?: state.nextDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = {
                viewModel.onEndDateChanged(it)
                showEndDatePicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit scheduled" else "Add scheduled") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedButton(
                onClick = { showNextDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next date: ${state.nextDate.substringBefore('T')}") }

            Text("Mode", style = MaterialTheme.typography.labelMedium)
            ScheduleMode.entries.chunked(3).forEach { rowModes ->
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    rowModes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.onModeChanged(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, rowModes.size)
                        ) {
                            Text(
                                when (mode) {
                                    ScheduleMode.ONCE -> "Once"
                                    ScheduleMode.DAILY -> "Daily"
                                    ScheduleMode.MONTHLY -> "Monthly"
                                    ScheduleMode.DAY_OF_WEEK -> "Weekly"
                                    ScheduleMode.CUSTOM -> "Custom"
                                }
                            )
                        }
                    }
                }
            }

            if (state.mode == ScheduleMode.CUSTOM) {
                OutlinedTextField(
                    value = state.everyN,
                    onValueChange = viewModel::onEveryNChanged,
                    label = { Text("Every N") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CustomPeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = state.customPeriod == p,
                            onClick = { viewModel.onCustomPeriodChanged(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, CustomPeriod.entries.size)
                        ) {
                            Text(
                                when (p) {
                                    CustomPeriod.WEEK -> "Week"
                                    CustomPeriod.MONTH -> "Month"
                                    CustomPeriod.YEAR -> "Year"
                                }
                            )
                        }
                    }
                }
                Text(
                    "Weekday follows the next date (one schedule per weekday).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.mode == ScheduleMode.DAY_OF_WEEK) {
                Text(
                    "Repeats weekly on the weekday of the next date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChanged(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size)
                    ) { Text(type.mmexCode) }
                }
            }

            ScheduleDropdown(
                label = "Account",
                options = state.accounts.map { it.id to it.name },
                selectedId = state.accountId,
                onSelected = viewModel::onAccountChanged
            )

            if (state.type == TransactionType.TRANSFER) {
                ScheduleDropdown(
                    label = "To account",
                    options = state.accounts.map { it.id to it.name },
                    selectedId = state.toAccountId,
                    onSelected = viewModel::onToAccountChanged
                )
            }

            val categoryLabel = state.categId?.let { state.categories.buildPath(it) }
                ?.takeIf { it.isNotEmpty() } ?: "Select category"
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(categoryLabel) }

            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::onAmountChanged,
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.type == TransactionType.TRANSFER) {
                OutlinedTextField(
                    value = state.toAmount,
                    onValueChange = viewModel::onToAmountChanged,
                    label = { Text("Destination amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (state.mode != ScheduleMode.ONCE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(state.endDate?.let { "End: $it" } ?: "End date (optional)")
                    }
                    if (state.endDate != null) {
                        TextButton(onClick = viewModel::onClearEndDate) { Text("Clear") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isEdit) "Save" else "Add")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDatePicker(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialMillis = remember(initialDate) {
        val datePart = initialDate.substringBefore('T')
        runCatching {
            java.time.LocalDate.parse(datePart)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        }.getOrNull()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.toString())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) { DatePicker(state = datePickerState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(
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

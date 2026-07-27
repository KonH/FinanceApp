package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.ui.util.formatAmount
import com.konhit.financeapp.domain.model.TransactionType
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ScheduledListViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var deleteId by remember { mutableStateOf<Long?>(null) }

    deleteId?.let { bdId ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete scheduled transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDelete(bdId)
                    deleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            if (!state.isReadOnly) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, "Add scheduled transaction")
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (state.items.isEmpty()) {
                item {
                    Text(
                        "No scheduled transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(state.items, key = { it.scheduled.bdId }) { item ->
                val s = item.scheduled
                val amountColor = when (s.type) {
                    TransactionType.DEPOSIT -> Color(0xFF2E7D32)
                    TransactionType.WITHDRAWAL -> Color(0xFFC62828)
                    TransactionType.TRANSFER -> Color(0xFF1565C0)
                }
                ListItem(
                    headlineContent = {
                        Text(
                            item.categoryPath.ifBlank { s.notes ?: s.type.mmexCode }
                        )
                    },
                    supportingContent = {
                        Text(
                            "${s.nextOccurrenceDate.substringBefore('T')}  ·  ${item.accountName}"
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatAmount(s.transAmount, null),
                                color = amountColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            if (!state.isReadOnly) {
                                IconButton(onClick = { onEdit(s.bdId) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, "Edit")
                                }
                                IconButton(onClick = { deleteId = s.bdId }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

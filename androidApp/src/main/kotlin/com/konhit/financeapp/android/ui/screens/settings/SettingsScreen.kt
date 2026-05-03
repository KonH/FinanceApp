package com.konhit.financeapp.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.AccessMode
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item {
                SectionHeader("Database")
                ListItem(
                    headlineContent = { Text("File ID") },
                    supportingContent = { Text(state.driveFileId ?: "Not configured") }
                )
                ListItem(
                    headlineContent = { Text("Last sync") },
                    supportingContent = { Text(state.lastSyncDisplay) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.onSyncClick() }) {
                            Icon(Icons.Default.Sync, "Sync now")
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Access mode") },
                    trailingContent = {
                        SingleChoiceSegmentedButtonRow {
                            AccessMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = state.accessMode == mode,
                                    onClick = { viewModel.onAccessModeChanged(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, AccessMode.entries.size)
                                ) { Text(if (mode == AccessMode.READ_WRITE) "Read-write" else "Read-only") }
                            }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item { SectionHeader("Accounts") }
            items(state.accounts, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(account.type) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.onDeleteAccount(account.id) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader("Categories (${state.categories.size})") }
            items(state.categories.take(20), key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.onDeleteCategory(category.id) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader("Currencies (${state.currencies.size})") }
            items(state.currencies.take(20), key = { it.id }) { currency ->
                ListItem(
                    headlineContent = { Text(currency.name) },
                    supportingContent = { Text(currency.currencySymbol ?: currency.pfxSymbol ?: "") }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

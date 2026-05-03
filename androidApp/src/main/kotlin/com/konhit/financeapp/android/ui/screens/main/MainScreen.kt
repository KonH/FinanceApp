package com.konhit.financeapp.android.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.SyncState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAccountClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: MainViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // Conflict dialog
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
                    IconButton(onClick = { viewModel.onSyncClick() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.searchQuery.isNotBlank()) {
                SearchResultsList(results = state.searchResults, isSearching = state.isSearching)
            } else {
                AccountList(accounts = state.accounts, onAccountClick = onAccountClick)
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
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search transactions…") },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun AccountList(accounts: List<Account>, onAccountClick: (Long) -> Unit) {
    LazyColumn {
        items(accounts, key = { it.id }) { account ->
            AccountRow(account = account, onClick = { onAccountClick(account.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(account.name) },
        supportingContent = { Text(account.type) },
        trailingContent = { Text("%.2f".format(account.balance)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SearchResultsList(
    results: List<com.konhit.financeapp.domain.model.Transaction>,
    isSearching: Boolean
) {
    if (isSearching) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn {
        items(results, key = { it.transId }) { tx ->
            ListItem(
                headlineContent = { Text(tx.notes ?: "(no notes)") },
                supportingContent = { Text(tx.transDate.substringBefore('T')) },
                trailingContent = { Text("%.2f".format(tx.transAmount)) }
            )
            HorizontalDivider()
        }
    }
}

package com.konhit.financeapp.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.drive.DriveAuthManager
import org.koin.androidx.compose.get
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onCurrenciesClick: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val driveAuthManager: DriveAuthManager = get()
    val state by viewModel.state.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            viewModel.onGoogleSignInResult(true)
        } catch (e: ApiException) {
            viewModel.onGoogleSignInResult(false)
        }
    }

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

            if (state.isGoogleDriveEnabled) {
                item {
                    SectionHeader("Google Drive")
                    if (state.isGoogleConnected) {
                        ListItem(
                            headlineContent = { Text("Connected") },
                            supportingContent = { Text(state.googleAccountEmail ?: "") },
                            trailingContent = {
                                TextButton(onClick = { viewModel.onGoogleSignOut() }) {
                                    Text("Disconnect")
                                }
                            }
                        )
                        if (state.driveFileId != null) {
                            ListItem(
                                headlineContent = { Text("Last sync") },
                                supportingContent = { Text(state.lastSyncDisplay) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.onSyncClick() }) {
                                        Icon(Icons.Default.Sync, "Sync now")
                                    }
                                }
                            )
                        }
                    } else {
                        ListItem(
                            headlineContent = { Text("Not connected") },
                            supportingContent = { Text("Connect to sync with Google Drive") },
                            trailingContent = {
                                TextButton(onClick = {
                                    signInLauncher.launch(driveAuthManager.signInClient.signInIntent)
                                }) {
                                    Text("Connect")
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            item {
                SectionHeader("Database")
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

            item {
                SectionHeader("Data")
                ListItem(
                    headlineContent = { Text("Accounts") },
                    modifier = Modifier.clickable(onClick = onAccountsClick)
                )
                ListItem(
                    headlineContent = { Text("Categories") },
                    modifier = Modifier.clickable(onClick = onCategoriesClick)
                )
                ListItem(
                    headlineContent = { Text("Currencies") },
                    modifier = Modifier.clickable(onClick = onCurrenciesClick)
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

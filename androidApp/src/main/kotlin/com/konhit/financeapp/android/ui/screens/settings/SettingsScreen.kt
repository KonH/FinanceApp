package com.konhit.financeapp.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.android.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.konhit.financeapp.android.ui.components.CategoryPickerDialog
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.drive.DriveAuthManager
import org.koin.androidx.compose.get
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onCurrenciesClick: () -> Unit,
    onCloseDatabase: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val driveAuthManager: DriveAuthManager = get()
    val state by viewModel.state.collectAsState()

    var categoryPickerType by remember { mutableStateOf<TransactionType?>(null) }
    categoryPickerType?.let { type ->
        CategoryPickerDialog(
            categories = state.categories,
            onSelect = { cat ->
                viewModel.onDefaultCategoryChanged(type, cat.id)
                categoryPickerType = null
            },
            onDismiss = { categoryPickerType = null }
        )
    }

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

            item {
                SectionHeader("About")
                ListItem(
                    headlineContent = { Text("Version") },
                    trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

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
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    val dbFilePath = state.dbFilePath
                    if (dbFilePath != null) {
                        Text(
                            text = state.dbType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dbFilePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = "Access mode",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AccessMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = state.accessMode == mode,
                                onClick = { viewModel.onAccessModeChanged(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, AccessMode.entries.size)
                            ) { Text(if (mode == AccessMode.READ_WRITE) "Read-write" else "Read-only") }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.closeDatabase()
                            onCloseDatabase()
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Close database")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SectionHeader("Options")
                TransactionType.entries.forEach { type ->
                    val typeName = when (type) {
                        TransactionType.DEPOSIT    -> "Deposit"
                        TransactionType.WITHDRAWAL -> "Withdrawal"
                        TransactionType.TRANSFER   -> "Transfer"
                    }
                    val defaultCatId = state.defaultCategoryIds[type]
                    val defaultCatLabel = defaultCatId
                        ?.let { state.categories.buildPath(it) }
                        ?: "Not set"

                    Text(
                        typeName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Default category") },
                        supportingContent = { Text(defaultCatLabel) },
                        trailingContent = {
                            if (defaultCatId != null) {
                                IconButton(onClick = { viewModel.onDefaultCategoryChanged(type, null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.clickable { categoryPickerType = type }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onUseLatestCategoryChanged(
                                    type,
                                    !(state.useLatestCategory[type] ?: false)
                                )
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        Checkbox(
                            checked = state.useLatestCategory[type] ?: false,
                            onCheckedChange = { viewModel.onUseLatestCategoryChanged(type, it) }
                        )
                        Text("Use latest category for $typeName")
                    }
                }
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

package com.konhit.financeapp.android.ui.screens.firstlaunch

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.konhit.financeapp.drive.DriveAuthManager
import org.koin.androidx.compose.get
import org.koin.androidx.compose.koinViewModel

@Composable
fun FirstLaunchScreen(onFileReady: () -> Unit) {
    val viewModel: FirstLaunchViewModel = koinViewModel()
    val driveAuthManager: DriveAuthManager = get()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            viewModel.onSignInResult(true)
        } catch (e: ApiException) {
            viewModel.onSignInResult(false)
        }
    }

    val drivePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val driveFileId = result.data?.data?.lastPathSegment ?: return@rememberLauncherForActivityResult
            viewModel.openExistingFile(driveFileId, onFileReady)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FinanceApp", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(48.dp))

        if (!state.isSignedIn) {
            Button(onClick = {
                signInLauncher.launch(driveAuthManager.signInClient.signInIntent)
            }) {
                Text("Sign in with Google")
            }
        } else {
            Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Create new file")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { /* launch Drive file picker */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open existing file from Drive")
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(err, color = MaterialTheme.colorScheme.error)
        }

        if (state.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New file name") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        if (newFileName.isNotBlank()) {
                            viewModel.createNewFile(newFileName.trim(), onFileReady)
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

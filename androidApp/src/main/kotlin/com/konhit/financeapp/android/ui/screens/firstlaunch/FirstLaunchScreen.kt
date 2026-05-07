package com.konhit.financeapp.android.ui.screens.firstlaunch

import android.app.Activity
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.konhit.financeapp.drive.DriveAuthManager
import org.koin.androidx.compose.get
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun FirstLaunchScreen(onFileReady: () -> Unit) {
    val viewModel: FirstLaunchViewModel = koinViewModel()
    val driveAuthManager: DriveAuthManager = get()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

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
            viewModel.onSignInResult(false, "Sign-in failed (code ${e.statusCode})")
        }
    }

    val localFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val destFile = File(context.cacheDir, "current.mmb")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.openLocalFile(destFile, onFileReady)
        }
    }

    val drivePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val destFile = java.io.File(context.cacheDir, "current.mmb")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            viewModel.openDriveFile(destFile, displayName, onFileReady)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FinanceApp", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create new file")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { localFileLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open local file")
        }

        if (state.isGoogleDriveEnabled) {
            if (!state.isSignedIn) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { signInLauncher.launch(driveAuthManager.signInClient.signInIntent) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Google Drive")
                }
            } else {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        drivePickerLauncher.launch(
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open file from Drive")
                }
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = err.lines().first(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(err)) }) {
                Text("Copy full error")
            }
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

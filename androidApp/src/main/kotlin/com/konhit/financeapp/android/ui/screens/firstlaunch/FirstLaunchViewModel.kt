package com.konhit.financeapp.android.ui.screens.firstlaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.usecase.InitialiseFileUseCase
import com.konhit.financeapp.domain.usecase.OpenFileUseCase
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.DriveFileClient
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FirstLaunchState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false
)

class FirstLaunchViewModel(
    private val authManager: DriveAuthManager,
    private val initialise: InitialiseFileUseCase,
    private val openFile: OpenFileUseCase,
    private val settings: SettingsRepository,
    private val cacheDir: File
) : ViewModel() {

    private val _state = MutableStateFlow(FirstLaunchState())
    val state: StateFlow<FirstLaunchState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(isSignedIn = authManager.isSignedIn())
    }

    fun onSignInResult(isSuccess: Boolean) {
        _state.value = _state.value.copy(isSignedIn = isSuccess)
    }

    fun createNewFile(fileName: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val file = File(cacheDir, "$fileName.mmb")
                initialise(file, fileName)
                onReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun openExistingFile(driveFileId: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                settings.saveDriveFileId(driveFileId)
                openFile()
                onReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

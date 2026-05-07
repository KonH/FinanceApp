package com.konhit.financeapp.android.ui.screens.firstlaunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.usecase.InitialiseFileUseCase
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.SyncCoordinator
import com.konhit.financeapp.feature.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FirstLaunchState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false,
    val isGoogleDriveEnabled: Boolean = false
)

class FirstLaunchViewModel(
    private val authManager: DriveAuthManager,
    private val initialise: InitialiseFileUseCase,
    private val syncCoordinator: SyncCoordinator,
    private val settings: SettingsRepository,
    private val featureFlags: FeatureFlags,
    private val cacheDir: File
) : ViewModel() {

    private val _state = MutableStateFlow(FirstLaunchState())
    val state: StateFlow<FirstLaunchState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            isSignedIn = authManager.isSignedIn(),
            isGoogleDriveEnabled = featureFlags.googleDrive
        )
    }

    fun onSignInResult(isSuccess: Boolean, error: String? = null) {
        _state.value = _state.value.copy(
            isSignedIn = isSuccess,
            error = if (!isSuccess) error ?: "Google sign-in failed" else null
        )
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

    fun openLocalFile(file: File, onReady: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                settings.saveLocalFilePath(file.absolutePath)
                settings.saveDriveFileId(null)
                val mode = settings.getAccessMode()
                syncCoordinator.openLocal(file, mode)
                onReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun openDriveFile(localFile: File, fileName: String?, onReady: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val mode = settings.getAccessMode()
                syncCoordinator.openLocal(localFile, mode)
                if (fileName != null && authManager.isSignedIn()) {
                    val driveId = syncCoordinator.findDriveFileId(fileName)
                    settings.saveDriveFileId(driveId)
                }
                onReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

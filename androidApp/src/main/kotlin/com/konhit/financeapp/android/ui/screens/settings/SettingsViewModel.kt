package com.konhit.financeapp.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.feature.FeatureFlags
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsState(
    val driveFileId: String? = null,
    val accessMode: AccessMode = AccessMode.READ_WRITE,
    val lastSyncDisplay: String = "Never",
    val isGoogleConnected: Boolean = false,
    val googleAccountEmail: String? = null,
    val isGoogleDriveEnabled: Boolean = false
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val authManager: DriveAuthManager,
    private val featureFlags: FeatureFlags
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val fileId = settings.getDriveFileId()
            val mode = settings.getAccessMode()
            val lastSync = settings.getLastSyncTime()?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Never"
            val account = authManager.getSignedInAccount()
            _state.update {
                it.copy(
                    driveFileId = fileId,
                    accessMode = mode,
                    lastSyncDisplay = lastSync,
                    isGoogleConnected = account != null,
                    googleAccountEmail = account?.email,
                    isGoogleDriveEnabled = featureFlags.googleDrive
                )
            }
        }
    }

    fun onAccessModeChanged(mode: AccessMode) {
        viewModelScope.launch {
            settings.saveAccessMode(mode)
            _state.update { it.copy(accessMode = mode) }
        }
    }

    fun onSyncClick() {
        viewModelScope.launch { syncCoordinator.uploadCurrent() }
    }

    fun onGoogleSignInResult(isSuccess: Boolean) {
        if (isSuccess) {
            val account = authManager.getSignedInAccount()
            _state.update {
                it.copy(isGoogleConnected = true, googleAccountEmail = account?.email)
            }
        }
    }

    fun onGoogleSignOut() {
        authManager.signInClient.signOut().addOnCompleteListener {
            _state.update { it.copy(isGoogleConnected = false, googleAccountEmail = null) }
        }
    }

}

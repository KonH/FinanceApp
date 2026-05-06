package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.SyncCoordinator
import java.io.File

class OpenFileUseCase(
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val authManager: DriveAuthManager
) {
    suspend operator fun invoke() {
        val mode = settings.getAccessMode()
        val driveFileId = settings.getDriveFileId()
        if (driveFileId != null && authManager.isSignedIn()) {
            syncCoordinator.downloadAndOpen(driveFileId, mode)
            return
        }
        val localPath = settings.getLocalFilePath()
        if (localPath != null) {
            syncCoordinator.openLocal(File(localPath), mode)
        }
    }
}

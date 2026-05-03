package com.konhit.financeapp.domain.usecase

import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.drive.SyncCoordinator

class OpenFileUseCase(
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator
) {
    suspend operator fun invoke() {
        val fileId = settings.getDriveFileId() ?: return
        val mode = settings.getAccessMode()
        syncCoordinator.downloadAndOpen(fileId, mode)
    }
}

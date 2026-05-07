package com.konhit.financeapp.drive

import com.konhit.financeapp.db.DatabaseFactory
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.SyncState
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

class SyncCoordinator(
    private val driveClient: DriveFileClient,
    private val conflictDetector: DriveConflictDetector,
    private val dbFactory: DatabaseFactory,
    private val dbHolder: DatabaseHolder,
    private val settings: SettingsRepository,
    private val authManager: DriveAuthManager,
    private val cacheDir: File
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun downloadAndOpen(fileId: String, accessMode: AccessMode) {
        _syncState.value = SyncState.Syncing
        try {
            val localFile = File(cacheDir, "current.mmb")

            val remoteTime = driveClient.getRemoteModifiedTime(fileId)
            val lastSyncMs = settings.getLastSyncTime()
            val lastSyncTime = lastSyncMs?.let { Instant.fromEpochMilliseconds(it) }

            when (val conflict = conflictDetector.check(remoteTime, lastSyncTime)) {
                is ConflictResult.Conflict -> {
                    if (localFile.exists()) {
                        openAndPersistLocalFile(localFile, accessMode)
                        _syncState.value = SyncState.Conflict(conflict.remoteTime, conflict.localSyncTime)
                        return
                    }
                    // No local copy — fall through and download remote
                    driveClient.download(fileId, localFile)
                }
                is ConflictResult.NoConflict -> {
                    driveClient.download(fileId, localFile)
                }
            }

            openAndPersistLocalFile(localFile, accessMode)
            settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            throw e
        }
    }

    suspend fun openLocal(file: File, accessMode: AccessMode) {
        openAndPersistLocalFile(file, accessMode)
    }

    suspend fun uploadCurrent() {
        if (!authManager.isSignedIn()) return
        val file = dbHolder.currentFile ?: return
        val fileId = settings.getDriveFileId() ?: return
        _syncState.value = SyncState.Syncing
        try {
            driveClient.upload(file, fileId)
            settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun resolveConflictKeepRemote(fileId: String, accessMode: AccessMode) {
        _syncState.value = SyncState.Syncing
        try {
            val localFile = File(cacheDir, "current.mmb")
            driveClient.download(fileId, localFile)
            openAndPersistLocalFile(localFile, accessMode)
            settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    suspend fun findDriveFileId(fileName: String): String? = try {
        driveClient.findFileIdByName(fileName)
    } catch (e: Exception) {
        null
    }

    suspend fun resolveConflictKeepLocal() {
        uploadCurrent()
    }

    private suspend fun openAndPersistLocalFile(file: File, @Suppress("UNUSED_PARAMETER") accessMode: AccessMode) {
        val conn = dbFactory.openExisting(file)
        val payeeId = conn.database.payeeQueries.selectFirst().executeAsOneOrNull() ?: -1L
        dbHolder.open(conn.database, conn.driver, file, payeeId)
        settings.saveLocalFilePath(file.absolutePath)
    }
}

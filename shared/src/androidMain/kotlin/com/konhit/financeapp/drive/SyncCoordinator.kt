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
                    _syncState.value = SyncState.Conflict(conflict.remoteTime, conflict.localSyncTime)
                    return
                }
                is ConflictResult.NoConflict -> {
                    driveClient.download(fileId, localFile)
                }
            }

            openLocalFile(localFile, accessMode)
            settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    suspend fun uploadCurrent() {
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
            openLocalFile(localFile, accessMode)
            settings.saveLastSyncTime(Clock.System.now().toEpochMilliseconds())
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    suspend fun resolveConflictKeepLocal() {
        uploadCurrent()
    }

    private fun openLocalFile(file: File, @Suppress("UNUSED_PARAMETER") accessMode: AccessMode) {
        // Read-only access mode is enforced at the repository layer (checkWritable()).
        // The database is always opened read-write at the driver level.
        val db = dbFactory.openExisting(file)
        val payeeId = db.payeeQueries.selectFirst().executeAsOneOrNull() ?: -1L
        dbHolder.open(db, file, payeeId)
    }
}

package com.konhit.financeapp.drive

import android.util.Log
import com.konhit.financeapp.db.DatabaseFactory
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.SyncState
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val localFile = File(cacheDir, "current.mmb")
        try {
            val remoteTime = driveClient.getRemoteModifiedTime(fileId)
            val lastSyncMs = settings.getLastSyncTime()
            val lastSyncTime = lastSyncMs?.let { Instant.fromEpochMilliseconds(it) }
            val pendingUpload = settings.getPendingUpload()

            when (val conflict = conflictDetector.check(remoteTime, lastSyncTime, pendingUpload)) {
                is ConflictResult.Conflict -> {
                    if (localFile.exists()) {
                        // Same bytes as Drive → false conflict (e.g. upload landed but
                        // lastSyncTime / pendingUpload were not persisted).
                        val remoteMd5 = driveClient.getRemoteMd5Checksum(fileId)
                        val localMd5 = localFile.md5HexOrNull()
                        if (remoteMd5 != null && localMd5 != null && remoteMd5.equals(localMd5, ignoreCase = true)) {
                            openAndPersistLocalFile(localFile, accessMode)
                            settings.saveLastSyncTime(remoteTime.toEpochMilliseconds())
                            settings.savePendingUpload(false)
                            _syncState.value = SyncState.Idle
                            return
                        }
                        openAndPersistLocalFile(localFile, accessMode)
                        _syncState.value = SyncState.Conflict(conflict.remoteTime, conflict.localSyncTime)
                        return
                    }
                    // No local copy — fall through and download remote
                    driveClient.download(fileId, localFile)
                    openAndPersistLocalFile(localFile, accessMode)
                    settings.saveLastSyncTime(remoteTime.toEpochMilliseconds())
                    settings.savePendingUpload(false)
                    _syncState.value = SyncState.Idle
                }
                is ConflictResult.KeepLocalAndUpload -> {
                    if (localFile.exists()) {
                        openAndPersistLocalFile(localFile, accessMode)
                        // Push local changes; uploadCurrent updates lastSync / pending flags.
                        uploadCurrent()
                        return
                    }
                    // Pending flag without a local file — treat as normal download.
                    driveClient.download(fileId, localFile)
                    openAndPersistLocalFile(localFile, accessMode)
                    settings.saveLastSyncTime(remoteTime.toEpochMilliseconds())
                    settings.savePendingUpload(false)
                    _syncState.value = SyncState.Idle
                }
                is ConflictResult.NoConflict -> {
                    driveClient.download(fileId, localFile)
                    openAndPersistLocalFile(localFile, accessMode)
                    settings.saveLastSyncTime(remoteTime.toEpochMilliseconds())
                    settings.savePendingUpload(false)
                    _syncState.value = SyncState.Idle
                }
            }
        } catch (e: Exception) {
            Log.e("SyncCoordinator", "downloadAndOpen failed", e)
            if (localFile.exists()) {
                openAndPersistLocalFile(localFile, accessMode)
                _syncState.value = SyncState.PendingSync
            } else {
                _syncState.value = SyncState.Error(e.message ?: "Sync failed")
                throw e
            }
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
            dbHolder.checkpoint()
            val (_, uploadedTime) = driveClient.upload(file, fileId)
            settings.saveLastSyncTime(uploadedTime.toEpochMilliseconds())
            settings.savePendingUpload(false)
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            Log.e("SyncCoordinator", "uploadCurrent failed", e)
            settings.savePendingUpload(true)
            _syncState.value = SyncState.PendingSync
        }
    }

    suspend fun resolveConflictKeepRemote(fileId: String, accessMode: AccessMode) {
        _syncState.value = SyncState.Syncing
        try {
            val localFile = File(cacheDir, "current.mmb")
            val remoteTime = driveClient.getRemoteModifiedTime(fileId)
            driveClient.download(fileId, localFile)
            openAndPersistLocalFile(localFile, accessMode)
            settings.saveLastSyncTime(remoteTime.toEpochMilliseconds())
            settings.savePendingUpload(false)
            _syncState.value = SyncState.Idle
        } catch (e: Exception) {
            Log.e("SyncCoordinator", "resolveConflictKeepRemote failed", e)
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

private fun File.md5HexOrNull(): String? = try {
    val digest = java.security.MessageDigest.getInstance("MD5")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    digest.digest().joinToString("") { b -> "%02x".format(b) }
} catch (_: Exception) {
    null
}

package com.konhit.financeapp.drive

import kotlinx.datetime.Instant

sealed class ConflictResult {
    /** Safe to download (or re-download) the remote file. */
    data object NoConflict : ConflictResult()

    /**
     * Local file has unuploaded changes and the remote is still the version we last
     * synced — keep local and push; do not show a conflict dialog.
     */
    data object KeepLocalAndUpload : ConflictResult()

    /** Remote changed while local also has unuploaded changes — ask the user. */
    data class Conflict(val remoteTime: Instant, val localSyncTime: Instant) : ConflictResult()
}

class DriveConflictDetector {
    /**
     * Decide how to open a Drive-backed file.
     *
     * A conflict dialog is only warranted when **both** are true:
     * - local has unuploaded changes (`pendingUpload`)
     * - remote `modifiedTime` is newer than the last successful sync
     *
     * Forcing a conflict on every `pendingUpload` (even when Drive was never touched
     * by another client) caused frequent false "Keep local / Use remote" dialogs —
     * e.g. after a failed `onStop` upload on a flaky network.
     */
    fun check(
        remoteTime: Instant,
        lastSyncTime: Instant?,
        pendingUpload: Boolean
    ): ConflictResult {
        val remoteChanged = lastSyncTime != null && remoteTime > lastSyncTime
        return when {
            pendingUpload && remoteChanged ->
                ConflictResult.Conflict(remoteTime, lastSyncTime!!)
            pendingUpload ->
                ConflictResult.KeepLocalAndUpload
            else ->
                ConflictResult.NoConflict
        }
    }
}

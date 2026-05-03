package com.konhit.financeapp.drive

import kotlinx.datetime.Instant

sealed class ConflictResult {
    data object NoConflict : ConflictResult()
    data class Conflict(val remoteTime: Instant, val localSyncTime: Instant) : ConflictResult()
}

class DriveConflictDetector {
    fun check(remoteTime: Instant, lastSyncTime: Instant?): ConflictResult {
        if (lastSyncTime == null) return ConflictResult.NoConflict
        return if (remoteTime > lastSyncTime) {
            ConflictResult.Conflict(remoteTime, lastSyncTime)
        } else {
            ConflictResult.NoConflict
        }
    }
}

package com.konhit.financeapp.domain.model

import kotlinx.datetime.Instant

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data object PendingSync : SyncState()
    data class Conflict(val remoteTime: Instant, val localSyncTime: Instant) : SyncState()
    data class Error(val message: String) : SyncState()
}

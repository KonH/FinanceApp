package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.AccessMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeAccessMode(): Flow<AccessMode>
    suspend fun getAccessMode(): AccessMode
    suspend fun saveAccessMode(mode: AccessMode)

    suspend fun getDriveFileId(): String?
    suspend fun saveDriveFileId(fileId: String?)

    suspend fun getLastSyncTime(): Long?
    suspend fun saveLastSyncTime(epochMillis: Long)
}

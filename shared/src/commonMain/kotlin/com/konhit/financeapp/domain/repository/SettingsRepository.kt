package com.konhit.financeapp.domain.repository

import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeAccessMode(): Flow<AccessMode>
    suspend fun getAccessMode(): AccessMode
    suspend fun saveAccessMode(mode: AccessMode)

    suspend fun getDriveFileId(): String?
    suspend fun saveDriveFileId(fileId: String?)

    suspend fun getLastSyncTime(): Long?
    suspend fun saveLastSyncTime(epochMillis: Long)

    suspend fun getLocalFilePath(): String?
    suspend fun saveLocalFilePath(path: String?)

    suspend fun getPendingUpload(): Boolean
    suspend fun savePendingUpload(pending: Boolean)

    suspend fun getDefaultCategoryId(type: TransactionType): Long?
    suspend fun saveDefaultCategoryId(type: TransactionType, id: Long?)

    suspend fun getUseLatestCategory(type: TransactionType): Boolean
    suspend fun saveUseLatestCategory(type: TransactionType, enabled: Boolean)

    fun observeBudgets(): Flow<Map<Long, Double>>
    suspend fun getBudgets(): Map<Long, Double>
    suspend fun saveBudget(currencyId: Long, amount: Double?)
}

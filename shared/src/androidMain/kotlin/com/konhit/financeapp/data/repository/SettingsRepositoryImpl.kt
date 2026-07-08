package com.konhit.financeapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val keyAccessMode     = stringPreferencesKey("access_mode")
    private val keyDriveFileId    = stringPreferencesKey("drive_file_id")
    private val keyLastSyncTime   = longPreferencesKey("last_sync_time")
    private val keyLocalFilePath  = stringPreferencesKey("local_file_path")
    private val keyPendingUpload  = booleanPreferencesKey("pending_upload")

    private val keyDefaultCatDeposit    = longPreferencesKey("default_cat_deposit")
    private val keyDefaultCatWithdrawal = longPreferencesKey("default_cat_withdrawal")
    private val keyDefaultCatTransfer   = longPreferencesKey("default_cat_transfer")
    private val keyUseLatestDeposit     = booleanPreferencesKey("use_latest_deposit")
    private val keyUseLatestWithdrawal  = booleanPreferencesKey("use_latest_withdrawal")
    private val keyUseLatestTransfer    = booleanPreferencesKey("use_latest_transfer")

    private val keyBudgets = stringPreferencesKey("budgets")

    private fun defaultCatKey(type: TransactionType) = when (type) {
        TransactionType.DEPOSIT    -> keyDefaultCatDeposit
        TransactionType.WITHDRAWAL -> keyDefaultCatWithdrawal
        TransactionType.TRANSFER   -> keyDefaultCatTransfer
    }

    private fun useLatestKey(type: TransactionType) = when (type) {
        TransactionType.DEPOSIT    -> keyUseLatestDeposit
        TransactionType.WITHDRAWAL -> keyUseLatestWithdrawal
        TransactionType.TRANSFER   -> keyUseLatestTransfer
    }

    override fun observeAccessMode(): Flow<AccessMode> =
        dataStore.data.map { prefs ->
            AccessMode.valueOf(prefs[keyAccessMode] ?: AccessMode.READ_WRITE.name)
        }

    override suspend fun getAccessMode(): AccessMode =
        observeAccessMode().first()

    override suspend fun saveAccessMode(mode: AccessMode) {
        dataStore.edit { it[keyAccessMode] = mode.name }
    }

    override suspend fun getDriveFileId(): String? =
        dataStore.data.first()[keyDriveFileId]

    override suspend fun saveDriveFileId(fileId: String?) {
        dataStore.edit { prefs ->
            if (fileId != null) prefs[keyDriveFileId] = fileId
            else prefs.remove(keyDriveFileId)
        }
    }

    override suspend fun getLastSyncTime(): Long? =
        dataStore.data.first()[keyLastSyncTime]

    override suspend fun saveLastSyncTime(epochMillis: Long) {
        dataStore.edit { it[keyLastSyncTime] = epochMillis }
    }

    override suspend fun getLocalFilePath(): String? =
        dataStore.data.first()[keyLocalFilePath]

    override suspend fun saveLocalFilePath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[keyLocalFilePath] = path
            else prefs.remove(keyLocalFilePath)
        }
    }

    override suspend fun getPendingUpload(): Boolean =
        dataStore.data.first()[keyPendingUpload] ?: false

    override suspend fun savePendingUpload(pending: Boolean) {
        dataStore.edit { it[keyPendingUpload] = pending }
    }

    override suspend fun getDefaultCategoryId(type: TransactionType): Long? =
        dataStore.data.first()[defaultCatKey(type)]

    override suspend fun saveDefaultCategoryId(type: TransactionType, id: Long?) {
        dataStore.edit { prefs ->
            val key = defaultCatKey(type)
            if (id != null) prefs[key] = id else prefs.remove(key)
        }
    }

    override suspend fun getUseLatestCategory(type: TransactionType): Boolean =
        dataStore.data.first()[useLatestKey(type)] ?: false

    override suspend fun saveUseLatestCategory(type: TransactionType, enabled: Boolean) {
        dataStore.edit { it[useLatestKey(type)] = enabled }
    }

    override fun observeBudgets(): Flow<Map<Long, Double>> =
        dataStore.data.map { prefs -> parseBudgets(prefs[keyBudgets]) }

    override suspend fun getBudgets(): Map<Long, Double> =
        observeBudgets().first()

    override suspend fun saveBudget(currencyId: Long, amount: Double?) {
        dataStore.edit { prefs ->
            val current = parseBudgets(prefs[keyBudgets]).toMutableMap()
            if (amount != null && amount > 0) current[currencyId] = amount else current.remove(currencyId)
            prefs[keyBudgets] = current.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    private fun parseBudgets(raw: String?): Map<Long, Double> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.toLongOrNull()
            val amount = parts.getOrNull(1)?.toDoubleOrNull()
            if (id != null && amount != null) id to amount else null
        }.toMap()
    }
}

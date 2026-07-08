package com.konhit.financeapp.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.feature.FeatureFlags
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsState(
    val driveFileId: String? = null,
    val accessMode: AccessMode = AccessMode.READ_WRITE,
    val lastSyncDisplay: String = "Never",
    val isGoogleConnected: Boolean = false,
    val googleAccountEmail: String? = null,
    val isGoogleDriveEnabled: Boolean = false,
    val dbFilePath: String? = null,
    val dbType: String = "",
    val categories: List<Category> = emptyList(),
    val defaultCategoryIds: Map<TransactionType, Long?> = emptyMap(),
    val useLatestCategory: Map<TransactionType, Boolean> = emptyMap(),
    val budgetCurrencies: List<Currency> = emptyList(),
    val budgets: Map<Long, Double> = emptyMap()
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val authManager: DriveAuthManager,
    private val featureFlags: FeatureFlags,
    private val dbHolder: DatabaseHolder,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
    private val currencyRepo: CurrencyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val fileId = settings.getDriveFileId()
            val mode = settings.getAccessMode()
            val lastSync = settings.getLastSyncTime()?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Never"
            val account = authManager.getSignedInAccount()
            _state.update {
                it.copy(
                    driveFileId = fileId,
                    accessMode = mode,
                    lastSyncDisplay = lastSync,
                    isGoogleConnected = account != null,
                    googleAccountEmail = account?.email,
                    isGoogleDriveEnabled = featureFlags.googleDrive,
                    dbFilePath = dbHolder.currentFile?.absolutePath,
                    dbType = if (fileId != null) "Google Drive" else "Local"
                )
            }
        }
        viewModelScope.launch {
            val cats = categoryRepo.getAll()
            val defaults = TransactionType.entries.associateWith { settings.getDefaultCategoryId(it) }
            val useLatest = TransactionType.entries.associateWith { settings.getUseLatestCategory(it) }
            _state.update { it.copy(categories = cats, defaultCategoryIds = defaults, useLatestCategory = useLatest) }
        }
        viewModelScope.launch {
            val allCurrencies = currencyRepo.getAll().associateBy { it.id }
            val currenciesInUse = accountRepo.getAll()
                .mapNotNull { allCurrencies[it.currencyId] }
                .distinctBy { it.id }
                .sortedBy { it.name }
            val budgets = settings.getBudgets()
            _state.update { it.copy(budgetCurrencies = currenciesInUse, budgets = budgets) }
        }
    }

    fun onAccessModeChanged(mode: AccessMode) {
        viewModelScope.launch {
            settings.saveAccessMode(mode)
            _state.update { it.copy(accessMode = mode) }
        }
    }

    fun onSyncClick() {
        viewModelScope.launch { syncCoordinator.uploadCurrent() }
    }

    fun onGoogleSignInResult(isSuccess: Boolean) {
        if (isSuccess) {
            val account = authManager.getSignedInAccount()
            _state.update {
                it.copy(isGoogleConnected = true, googleAccountEmail = account?.email)
            }
        }
    }

    fun onGoogleSignOut() {
        authManager.signInClient.signOut().addOnCompleteListener {
            _state.update { it.copy(isGoogleConnected = false, googleAccountEmail = null) }
        }
    }

    fun closeDatabase() {
        dbHolder.close()
        viewModelScope.launch {
            settings.saveLocalFilePath(null)
            settings.saveDriveFileId(null)
        }
    }

    fun onDefaultCategoryChanged(type: TransactionType, id: Long?) {
        viewModelScope.launch {
            settings.saveDefaultCategoryId(type, id)
            _state.update { it.copy(defaultCategoryIds = it.defaultCategoryIds + (type to id)) }
        }
    }

    fun onUseLatestCategoryChanged(type: TransactionType, enabled: Boolean) {
        viewModelScope.launch {
            settings.saveUseLatestCategory(type, enabled)
            _state.update { it.copy(useLatestCategory = it.useLatestCategory + (type to enabled)) }
        }
    }

    fun onBudgetChanged(currencyId: Long, amount: Double?) {
        viewModelScope.launch {
            settings.saveBudget(currencyId, amount)
            _state.update {
                val budgets = if (amount != null && amount > 0) {
                    it.budgets + (currencyId to amount)
                } else {
                    it.budgets - currencyId
                }
                it.copy(budgets = budgets)
            }
        }
    }
}

package com.konhit.financeapp.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
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
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList()
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val currencyRepo: CurrencyRepository,
    private val syncCoordinator: SyncCoordinator
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
            _state.update { it.copy(driveFileId = fileId, accessMode = mode, lastSyncDisplay = lastSync) }
        }
        viewModelScope.launch {
            accountRepo.observeAll().collect { accounts ->
                _state.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            categoryRepo.observeAll().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            currencyRepo.observeAll().collect { currencies ->
                _state.update { it.copy(currencies = currencies) }
            }
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

    fun onDeleteAccount(id: Long) {
        viewModelScope.launch { accountRepo.delete(id) }
    }

    fun onDeleteCategory(id: Long) {
        viewModelScope.launch {
            if (!categoryRepo.hasTransactions(id)) categoryRepo.delete(id)
        }
    }
}

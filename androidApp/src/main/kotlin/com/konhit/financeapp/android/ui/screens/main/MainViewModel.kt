package com.konhit.financeapp.android.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.android.ui.store.BalanceVisibilityStore
import com.konhit.financeapp.android.ui.store.HiddenAccountsStore
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.SyncState
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountGroup(
    val type: String,
    val accounts: List<Account>,
    val totalsByCurrency: List<Pair<Currency, Double>>
)

data class MainState(
    val accountGroups: List<AccountGroup> = emptyList(),
    val currencies: Map<Long, Currency> = emptyMap(),
    val totalsByCurrency: List<Pair<Currency, Double>> = emptyList(),
    val syncState: SyncState = SyncState.Idle,
    val isReadOnly: Boolean = false,
    val balanceVisible: Boolean = true
)

class MainViewModel(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val currencyRepo: CurrencyRepository,
    private val categoryRepo: CategoryRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val balanceVisibilityStore: BalanceVisibilityStore,
    private val hiddenAccountsStore: HiddenAccountsStore
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepo.observeAll().collect { loadAccounts() }
        }
        viewModelScope.launch {
            transactionRepo.observeAnyChange().collect { loadAccounts() }
        }
        viewModelScope.launch {
            hiddenAccountsStore.hiddenIds.collect { loadAccounts() }
        }
        viewModelScope.launch {
            settings.observeAccessMode().collect { mode ->
                _state.update { it.copy(isReadOnly = mode == AccessMode.READ_ONLY) }
            }
        }
        viewModelScope.launch {
            syncCoordinator.syncState.collect { syncState ->
                _state.update { it.copy(syncState = syncState) }
            }
        }
        viewModelScope.launch {
            balanceVisibilityStore.isVisible.collect { visible ->
                _state.update { it.copy(balanceVisible = visible) }
            }
        }
        loadAccounts()
    }

    fun onToggleBalanceVisibility() {
        balanceVisibilityStore.toggle()
    }

    fun onSyncClick() {
        viewModelScope.launch { syncCoordinator.uploadCurrent() }
    }

    fun onConflictKeepLocal() {
        viewModelScope.launch { syncCoordinator.resolveConflictKeepLocal() }
    }

    fun onConflictKeepRemote() {
        viewModelScope.launch {
            val fileId = settings.getDriveFileId() ?: return@launch
            val mode = settings.getAccessMode()
            syncCoordinator.resolveConflictKeepRemote(fileId, mode)
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val hiddenIds = hiddenAccountsStore.hiddenIds.first()
            val accounts = accountRepo.getAll().filter { it.id !in hiddenIds }
            val allCurrencies = currencyRepo.getAll().associateBy { it.id }
            val groups = accounts
                .groupBy { it.type }
                .map { (type, accs) ->
                    val groupTotals = accs
                        .groupBy { it.currencyId }
                        .mapNotNull { (cid, grouped) ->
                            allCurrencies[cid]?.let { it to grouped.sumOf { a -> a.balance } }
                        }
                    AccountGroup(type, accs, groupTotals)
                }
            val totals = accounts
                .groupBy { it.currencyId }
                .mapNotNull { (cid, accs) ->
                    allCurrencies[cid]?.let { it to accs.sumOf { a -> a.balance } }
                }
            _state.update { it.copy(accountGroups = groups, currencies = allCurrencies, totalsByCurrency = totals) }
        }
    }
}

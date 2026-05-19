package com.konhit.financeapp.android.ui.screens.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.android.ui.store.BalanceVisibilityStore
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AccountState(
    val account: Account? = null,
    val transactions: List<Transaction> = emptyList(),
    val isReadOnly: Boolean = false,
    val categories: Map<Long, String> = emptyMap(),
    val accountCurrencies: Map<Long, Currency> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val balanceAtDate: Double? = null,
    val balanceVisible: Boolean = true
)

class AccountViewModel(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val currencyRepo: CurrencyRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val balanceVisibilityStore: BalanceVisibilityStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle["accountId"])

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.observeAccessMode().collect { mode ->
                _state.update { it.copy(isReadOnly = mode == AccessMode.READ_ONLY) }
            }
        }
        viewModelScope.launch {
            balanceVisibilityStore.isVisible.collect { visible ->
                _state.update { it.copy(balanceVisible = visible) }
            }
        }
        viewModelScope.launch {
            val allCats = categoryRepo.getAll()
            val cats = allCats.associate { it.id to allCats.buildPath(it.id) }
            _state.update { it.copy(categories = cats) }
        }
        viewModelScope.launch {
            val allAccounts = accountRepo.getAll()
            val allCurrencies = currencyRepo.getAll().associateBy { it.id }
            val accountCurrencies = allAccounts.associate { acc ->
                acc.id to (allCurrencies[acc.currencyId] ?: Currency(acc.currencyId, "", null, null, null, null, null, null))
            }
            _state.update { it.copy(accountCurrencies = accountCurrencies) }
        }
        viewModelScope.launch {
            transactionRepo.observeByAccount(accountId).collect { txs ->
                _state.update { it.copy(transactions = txs) }
                val account = accountRepo.getById(accountId)
                _state.update { it.copy(account = account) }
            }
        }
    }

    fun onDeleteTransaction(transId: Long) {
        viewModelScope.launch {
            transactionRepo.delete(transId)
            syncCoordinator.uploadCurrent()
        }
    }

    fun onSelectDate(date: LocalDate) {
        viewModelScope.launch {
            val bal = accountRepo.getBalanceAtDate(accountId, date.toString())
            _state.update { it.copy(selectedDate = date, balanceAtDate = bal) }
        }
    }

    fun onClearDate() {
        _state.update { it.copy(selectedDate = null, balanceAtDate = null) }
    }
}

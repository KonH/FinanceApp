package com.konhit.financeapp.android.ui.screens.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountState(
    val account: Account? = null,
    val transactions: List<Transaction> = emptyList(),
    val isReadOnly: Boolean = false
)

class AccountViewModel(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
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
            transactionRepo.observeByAccount(accountId).collect { txs ->
                _state.update { it.copy(transactions = txs) }
            }
        }
        loadAccount()
    }

    fun onDeleteTransaction(transId: Long) {
        viewModelScope.launch {
            transactionRepo.delete(transId)
            syncCoordinator.uploadCurrent()
            loadAccount()
        }
    }

    private fun loadAccount() {
        viewModelScope.launch {
            val account = accountRepo.getById(accountId)
            _state.update { it.copy(account = account) }
        }
    }
}

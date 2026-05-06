package com.konhit.financeapp.android.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountDialogState(
    val id: Long? = null,
    val name: String = "",
    val type: String = "Checking",
    val initialBal: String = "0",
    val currencyId: Long = -1L
)

data class AccountsState(
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val dialog: AccountDialogState? = null
)

class AccountsViewModel(
    private val accountRepo: AccountRepository,
    private val currencyRepo: CurrencyRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepo.observeAll().collect { accounts ->
                _state.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            currencyRepo.observeAll().collect { currencies ->
                _state.update { it.copy(currencies = currencies) }
            }
        }
    }

    fun onAddClick() {
        val defaultCurrencyId = _state.value.currencies.firstOrNull()?.id ?: -1L
        _state.update { it.copy(dialog = AccountDialogState(currencyId = defaultCurrencyId)) }
    }

    fun onEditClick(account: Account) {
        _state.update {
            it.copy(
                dialog = AccountDialogState(
                    id = account.id,
                    name = account.name,
                    type = account.type,
                    initialBal = account.initialBal.toString(),
                    currencyId = account.currencyId
                )
            )
        }
    }

    fun onDialogNameChange(name: String) = _state.update { it.copy(dialog = it.dialog?.copy(name = name)) }
    fun onDialogTypeChange(type: String) = _state.update { it.copy(dialog = it.dialog?.copy(type = type)) }
    fun onDialogInitialBalChange(value: String) = _state.update { it.copy(dialog = it.dialog?.copy(initialBal = value)) }
    fun onDialogCurrencyChange(currencyId: Long) = _state.update { it.copy(dialog = it.dialog?.copy(currencyId = currencyId)) }
    fun onDialogDismiss() = _state.update { it.copy(dialog = null) }

    fun onDialogSave() {
        val dialog = _state.value.dialog ?: return
        viewModelScope.launch {
            val initialBal = dialog.initialBal.toDoubleOrNull() ?: 0.0
            if (dialog.id == null) {
                accountRepo.insert(
                    Account(
                        id = System.currentTimeMillis() * 1000L,
                        name = dialog.name,
                        type = dialog.type,
                        initialBal = initialBal,
                        currencyId = dialog.currencyId,
                        status = ""
                    )
                )
            } else {
                accountRepo.update(
                    Account(
                        id = dialog.id,
                        name = dialog.name,
                        type = dialog.type,
                        initialBal = initialBal,
                        currencyId = dialog.currencyId,
                        status = ""
                    )
                )
            }
            syncCoordinator.uploadCurrent()
            _state.update { it.copy(dialog = null) }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            accountRepo.delete(id)
            syncCoordinator.uploadCurrent()
        }
    }
}

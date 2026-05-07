package com.konhit.financeapp.android.ui.screens.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CurrencyDialogState(
    val id: Long? = null,
    val name: String = "",
    val symbol: String = "",
    val pfxSymbol: String = ""
)

data class CurrenciesState(
    val currencies: List<Currency> = emptyList(),
    val dialog: CurrencyDialogState? = null
)

class CurrenciesViewModel(
    private val currencyRepo: CurrencyRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(CurrenciesState())
    val state: StateFlow<CurrenciesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            currencyRepo.observeAll().collect { currencies ->
                _state.update { it.copy(currencies = currencies) }
            }
        }
    }

    fun onAddClick() = _state.update { it.copy(dialog = CurrencyDialogState()) }

    fun onEditClick(currency: Currency) = _state.update {
        it.copy(
            dialog = CurrencyDialogState(
                id        = currency.id,
                name      = currency.name,
                symbol    = currency.currencySymbol ?: "",
                pfxSymbol = currency.pfxSymbol ?: ""
            )
        )
    }

    fun onDialogNameChange(name: String) = _state.update { it.copy(dialog = it.dialog?.copy(name = name)) }
    fun onDialogSymbolChange(symbol: String) = _state.update { it.copy(dialog = it.dialog?.copy(symbol = symbol)) }
    fun onDialogPfxChange(pfx: String) = _state.update { it.copy(dialog = it.dialog?.copy(pfxSymbol = pfx)) }
    fun onDialogDismiss() = _state.update { it.copy(dialog = null) }

    fun onDialogSave() {
        val dialog = _state.value.dialog ?: return
        viewModelScope.launch {
            val currency = Currency(
                id             = dialog.id ?: (System.currentTimeMillis() * 1000L),
                name           = dialog.name,
                currencySymbol = dialog.symbol.ifBlank { null },
                pfxSymbol      = dialog.pfxSymbol.ifBlank { null },
                sfxSymbol      = null,
                decimalPoint   = null,
                groupSeparator = null,
                scale          = null
            )
            if (dialog.id == null) currencyRepo.insert(currency) else currencyRepo.update(currency)
            syncCoordinator.uploadCurrent()
            _state.update { it.copy(dialog = null) }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            currencyRepo.delete(id)
            syncCoordinator.uploadCurrent()
        }
    }
}

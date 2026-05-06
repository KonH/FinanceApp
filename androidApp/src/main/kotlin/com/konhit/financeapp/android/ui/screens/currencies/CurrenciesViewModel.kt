package com.konhit.financeapp.android.ui.screens.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CurrenciesState(
    val currencies: List<Currency> = emptyList()
)

class CurrenciesViewModel(
    private val currencyRepo: CurrencyRepository
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
}

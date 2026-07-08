package com.konhit.financeapp.android.ui.screens.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionFilter(
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val currencyId: Long? = null,
    val categoryId: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null
)

data class CurrencyFlowSummary(
    val currency: Currency,
    val income: Double,
    val expense: Double,
    val net: Double
)

data class FilterState(
    val filter: TransactionFilter = TransactionFilter(),
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val categoryList: List<Category> = emptyList(),
    val categories: Map<Long, String> = emptyMap(),
    val accountCurrenciesMap: Map<Long, Currency> = emptyMap(),
    val transactions: List<Transaction> = emptyList(),
    val flowSummaries: List<CurrencyFlowSummary> = emptyList(),
    val isFiltersExpanded: Boolean = true
)

class FilterViewModel(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val currencyRepo: CurrencyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialAccountId: Long? =
        savedStateHandle.get<Long>("accountId")?.takeIf { it != -1L }

    private val _state = MutableStateFlow(
        FilterState(filter = TransactionFilter(accountId = initialAccountId))
    )
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private var allTransactions: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            val accounts = accountRepo.getAll()
            val allCurrencies = currencyRepo.getAll()
            val currMap = allCurrencies.associateBy { it.id }
            val accountCurrenciesMap = accounts.associate { acc ->
                acc.id to (currMap[acc.currencyId]
                    ?: Currency(acc.currencyId, "", null, null, null, null, null, null))
            }
            _state.update {
                it.copy(
                    accounts = accounts,
                    currencies = allCurrencies,
                    accountCurrenciesMap = accountCurrenciesMap
                )
            }
            recompute()
        }
        viewModelScope.launch {
            val cats = categoryRepo.getAll()
            _state.update { it.copy(categoryList = cats, categories = cats.associate { it.id to it.name }) }
        }
        viewModelScope.launch {
            transactionRepo.observeAll().collect { txs ->
                allTransactions = txs
                recompute()
            }
        }
    }

    fun onFilterChanged(filter: TransactionFilter) {
        _state.update { it.copy(filter = filter) }
        recompute()
    }

    fun onToggleFiltersExpanded() {
        _state.update { it.copy(isFiltersExpanded = !it.isFiltersExpanded) }
    }

    private fun recompute() {
        val s = _state.value
        val filtered = applyFilter(allTransactions, s.filter, s.accountCurrenciesMap)
        _state.update {
            it.copy(
                transactions = filtered,
                flowSummaries = computeFlowSummaries(filtered, s.accountCurrenciesMap)
            )
        }
    }

    private fun applyFilter(
        transactions: List<Transaction>,
        filter: TransactionFilter,
        accountCurrencies: Map<Long, Currency>
    ): List<Transaction> = transactions.filter { tx ->
        (filter.type == null || tx.type == filter.type) &&
        (filter.accountId == null || tx.accountId == filter.accountId || tx.toAccountId == filter.accountId) &&
        (filter.currencyId == null || accountCurrencies[tx.accountId]?.id == filter.currencyId) &&
        (filter.categoryId == null || tx.categId == filter.categoryId) &&
        (filter.startDate == null || tx.transDate.substringBefore('T') >= filter.startDate) &&
        (filter.endDate == null || tx.transDate.substringBefore('T') <= filter.endDate) &&
        (filter.minAmount == null || tx.transAmount >= filter.minAmount) &&
        (filter.maxAmount == null || tx.transAmount <= filter.maxAmount)
    }

    private fun computeFlowSummaries(
        transactions: List<Transaction>,
        accountCurrencies: Map<Long, Currency>
    ): List<CurrencyFlowSummary> {
        val income = mutableMapOf<Long, Double>()
        val expense = mutableMapOf<Long, Double>()
        for (tx in transactions) {
            val currency = accountCurrencies[tx.accountId] ?: continue
            when (tx.type) {
                TransactionType.DEPOSIT    -> income[currency.id] = (income[currency.id] ?: 0.0) + tx.transAmount
                TransactionType.WITHDRAWAL -> expense[currency.id] = (expense[currency.id] ?: 0.0) + tx.transAmount
                TransactionType.TRANSFER   -> {}
            }
        }
        val currencyById = accountCurrencies.values.associateBy { it.id }
        val currencyIds = income.keys + expense.keys
        return currencyIds.mapNotNull { id ->
            currencyById[id]?.let { currency ->
                val inc = income[id] ?: 0.0
                val exp = expense[id] ?: 0.0
                CurrencyFlowSummary(currency = currency, income = inc, expense = exp, net = inc - exp)
            }
        }
    }
}

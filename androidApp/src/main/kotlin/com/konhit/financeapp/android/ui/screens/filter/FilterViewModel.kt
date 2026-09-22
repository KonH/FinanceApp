package com.konhit.financeapp.android.ui.screens.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.Currency
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.domain.rates.BaseCurrencyBalance
import com.konhit.financeapp.domain.rates.ExchangeRates
import com.konhit.financeapp.domain.rates.FilteredBalance
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.domain.usecase.EnsureRatesUseCase
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransactionFilter(
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val currencyId: Long? = null,
    val categoryId: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val comment: String? = null
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
    val isFiltersExpanded: Boolean = true,
    val isReadOnly: Boolean = false,
    /** Currency the filtered balance is converted into; null = no conversion. */
    val baseCurrencyId: Long? = null,
    /** Currencies present in the filtered transactions (plus the current choice). */
    val baseCurrencyOptions: List<Currency> = emptyList(),
    val baseBalance: BaseCurrencyBalance? = null,
    /** 0..100 while exchange rates are downloading, else null. */
    val rateProgress: Int? = null,
    val rateError: String? = null
)

class FilterViewModel(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val currencyRepo: CurrencyRepository,
    private val ensureRates: EnsureRatesUseCase,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialAccountId: Long? =
        savedStateHandle.get<Long>("accountId")?.takeIf { it != -1L }

    private val _state = MutableStateFlow(
        FilterState(filter = TransactionFilter(accountId = initialAccountId))
    )
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private var allTransactions: List<Transaction> = emptyList()
    private var ratesJob: Job? = null

    init {
        viewModelScope.launch {
            settings.observeAccessMode().collect { mode ->
                _state.update { it.copy(isReadOnly = mode == AccessMode.READ_ONLY) }
            }
        }
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
            _state.update { it.copy(categoryList = cats, categories = cats.associate { it.id to cats.buildPath(it.id) }) }
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

    fun onBaseCurrencyChanged(currencyId: Long?) {
        _state.update { it.copy(baseCurrencyId = currencyId) }
        recompute()
    }

    fun onRetryRates() = refreshBaseBalance()

    fun onToggleFiltersExpanded() {
        _state.update { it.copy(isFiltersExpanded = !it.isFiltersExpanded) }
    }

    fun onDeleteTransaction(transId: Long) {
        viewModelScope.launch {
            transactionRepo.delete(transId)
            syncCoordinator.uploadCurrent()
        }
    }

    private fun recompute() {
        val s = _state.value
        val filtered = applyFilter(allTransactions, s.filter, s.accountCurrenciesMap)
        _state.update {
            it.copy(
                transactions = filtered,
                flowSummaries = computeFlowSummaries(filtered, s.accountCurrenciesMap),
                baseCurrencyOptions = baseCurrencyOptions(filtered, s)
            )
        }
        refreshBaseBalance()
    }

    private fun baseCurrencyOptions(filtered: List<Transaction>, s: FilterState): List<Currency> {
        val ids = filtered.flatMapTo(mutableSetOf()) { tx ->
            listOfNotNull(
                s.accountCurrenciesMap[tx.accountId]?.id,
                s.accountCurrenciesMap[tx.toAccountId]?.takeIf { tx.type == TransactionType.TRANSFER }?.id
            )
        }
        s.baseCurrencyId?.let { ids += it }
        return s.currencies.filter { it.id in ids }.sortedBy { it.name }
    }

    /**
     * Converts the filtered list into the base currency at each transaction's
     * date, downloading whatever rates the cache lacks first. Restarted (and the
     * previous run cancelled) on every filter change, after a short debounce.
     */
    private fun refreshBaseBalance() {
        ratesJob?.cancel()
        val s = _state.value
        val base = s.currencies.find { it.id == s.baseCurrencyId }
        if (base == null) {
            _state.update { it.copy(baseBalance = null, rateProgress = null, rateError = null) }
            return
        }
        val today = LocalDate.now().toString()
        val baseCode = ExchangeRates.codeOf(base.currencySymbol, base.name)
        val accountCodes = s.accountCurrenciesMap.mapValues { (_, c) -> ExchangeRates.codeOf(c.currencySymbol, c.name) }
        val legs = FilteredBalance.legs(s.transactions, s.filter.accountId, accountCodes, today)
        val needs = FilteredBalance.needs(legs, baseCode)

        ratesJob = viewModelScope.launch {
            delay(RATES_DEBOUNCE_MS)
            val rates = ensureRates(needs, today) { percent ->
                _state.update {
                    it.copy(baseBalance = if (percent == 0) null else it.baseBalance, rateProgress = percent, rateError = null)
                }
            }
            val result = FilteredBalance.compute(legs, baseCode, rates.table)
            _state.update {
                it.copy(
                    baseBalance = result.balance,
                    rateProgress = null,
                    rateError = when {
                        result.balance != null -> null
                        rates.failed -> "Couldn't load exchange rates. Check the connection and retry."
                        else -> "No exchange rate for ${result.missingCodes.joinToString()}"
                    }
                )
            }
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
        (filter.maxAmount == null || tx.transAmount <= filter.maxAmount) &&
        (filter.comment.isNullOrBlank() || (tx.notes ?: "").contains(filter.comment, ignoreCase = true))
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

    private companion object {
        const val RATES_DEBOUNCE_MS = 300L
    }
}

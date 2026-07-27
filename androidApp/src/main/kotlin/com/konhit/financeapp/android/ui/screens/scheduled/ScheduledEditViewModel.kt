package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.CustomPeriod
import com.konhit.financeapp.domain.model.MmexDateFormat
import com.konhit.financeapp.domain.model.ScheduleDateMath
import com.konhit.financeapp.domain.model.ScheduleMode
import com.konhit.financeapp.domain.model.ScheduleRecurrence
import com.konhit.financeapp.domain.model.ScheduledTransaction
import com.konhit.financeapp.domain.model.TransactionType
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.ScheduledTransactionRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ScheduledEditState(
    val isEdit: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val type: TransactionType = TransactionType.WITHDRAWAL,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categId: Long? = null,
    val nextDate: String = MmexDateFormat.formatTransDate(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ),
    val mode: ScheduleMode = ScheduleMode.MONTHLY,
    val everyN: String = "1",
    val customPeriod: CustomPeriod = CustomPeriod.MONTH,
    val amount: String = "",
    val toAmount: String = "",
    val notes: String = "",
    val endDate: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class ScheduledEditViewModel(
    private val scheduledRepo: ScheduledTransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator,
    private val dbHolder: DatabaseHolder,
    private val editBdId: Long?
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduledEditState(isEdit = editBdId != null))
    val state: StateFlow<ScheduledEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepo.getAll()
            val categories = categoryRepo.getAll()
            _state.update { it.copy(accounts = accounts, categories = categories) }
            if (editBdId != null) {
                load(editBdId)
            } else {
                val defaultCat = settings.getDefaultCategoryId(_state.value.type)
                _state.update {
                    it.copy(
                        accountId = accounts.firstOrNull()?.id,
                        categId = defaultCat
                    )
                }
            }
        }
    }

    private suspend fun load(bdId: Long) {
        val s = scheduledRepo.getById(bdId) ?: return
        val rec = s.recurrence
        _state.update {
            it.copy(
                type = s.type,
                accountId = s.accountId,
                toAccountId = s.toAccountId.takeIf { id -> id != -1L },
                categId = s.categId,
                nextDate = s.nextOccurrenceDate,
                mode = rec.mode,
                everyN = rec.everyN.toString(),
                customPeriod = rec.customPeriod,
                amount = s.transAmount.toDisplayString(),
                toAmount = s.toTransAmount.toDisplayString(),
                notes = s.notes ?: "",
                endDate = ScheduleDateMath.decodeEndDate(s.transactionNumber)?.toString()
            )
        }
    }

    fun onTypeChanged(type: TransactionType) {
        _state.update { it.copy(type = type) }
        if (editBdId == null) {
            viewModelScope.launch {
                settings.getDefaultCategoryId(type)?.let { id ->
                    _state.update { it.copy(categId = id) }
                }
            }
        }
    }

    fun onAccountChanged(id: Long) = _state.update { it.copy(accountId = id) }
    fun onToAccountChanged(id: Long) = _state.update { it.copy(toAccountId = id) }
    fun onCategorySelected(category: Category) = _state.update { it.copy(categId = category.id) }
    fun onNextDateChanged(date: String) = _state.update { it.copy(nextDate = date) }
    fun onModeChanged(mode: ScheduleMode) = _state.update { it.copy(mode = mode) }
    fun onEveryNChanged(n: String) = _state.update { it.copy(everyN = n.filter { ch -> ch.isDigit() }) }
    fun onCustomPeriodChanged(p: CustomPeriod) = _state.update { it.copy(customPeriod = p) }
    fun onAmountChanged(v: String) = _state.update { it.copy(amount = v) }
    fun onToAmountChanged(v: String) = _state.update { it.copy(toAmount = v) }
    fun onNotesChanged(v: String) = _state.update { it.copy(notes = v) }
    fun onEndDateChanged(date: String?) = _state.update { it.copy(endDate = date) }
    fun onClearEndDate() = _state.update { it.copy(endDate = null) }

    fun onSave() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val s = _state.value
                val accountId = s.accountId ?: error("Account required")
                val categId = s.categId ?: error("Category required")
                val amount = s.amount.toDoubleOrNull() ?: error("Invalid amount")
                val toAmount = s.toAmount.toDoubleOrNull() ?: 0.0
                val toAccountId = if (s.type == TransactionType.TRANSFER) {
                    s.toAccountId ?: error("To-account required for transfer")
                } else -1L

                val recurrence = ScheduleRecurrence(
                    mode = s.mode,
                    everyN = s.everyN.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    customPeriod = s.customPeriod
                )
                val endLocal: LocalDate? = s.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val start = ScheduleDateMath.parseDate(s.nextDate)

                val (repeatsBase, _) = recurrence.toRepeatsAndOccurrences(-1)
                val usesInterval = recurrence.usesOccurrencesAsInterval()

                val numOccurrences: Int? = when {
                    s.mode == ScheduleMode.ONCE -> 1
                    usesInterval -> {
                        // Interval stored in NUMOCCURRENCES; end date via TRANSACTIONNUMBER
                        recurrence.everyN.coerceAtLeast(1).let { n ->
                            when (recurrence.customPeriod) {
                                CustomPeriod.WEEK -> n * 7
                                CustomPeriod.MONTH -> n
                                CustomPeriod.YEAR -> n * 12
                            }
                        }
                    }
                    endLocal != null -> ScheduleDateMath.countRemaining(
                        start = start,
                        end = endLocal,
                        repeats = repeatsBase,
                        intervalForEveryX = null
                    ).coerceAtLeast(1)
                    else -> -1
                }

                val (repeats, _) = recurrence.toRepeatsAndOccurrences(numOccurrences ?: -1)
                val transactionNumber = if (usesInterval) ScheduleDateMath.encodeEndDate(endLocal) else null

                val scheduled = ScheduledTransaction(
                    bdId = editBdId ?: (System.currentTimeMillis() * 1000L),
                    accountId = accountId,
                    toAccountId = toAccountId,
                    payeeId = dbHolder.defaultPayeeId,
                    type = s.type,
                    transAmount = amount,
                    toTransAmount = if (s.type == TransactionType.TRANSFER) toAmount else 0.0,
                    categId = categId,
                    notes = s.notes.ifBlank { null },
                    nextOccurrenceDate = if (s.nextDate.contains('T')) s.nextDate
                        else MmexDateFormat.formatTransDate(start),
                    repeats = repeats,
                    numOccurrences = numOccurrences,
                    status = "",
                    transactionNumber = transactionNumber,
                    color = -1,
                    followupId = -1L
                )

                if (editBdId != null) scheduledRepo.update(scheduled)
                else scheduledRepo.insert(scheduled)

                syncCoordinator.uploadCurrent()
                _state.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun Double.toDisplayString(): String =
        if (this == kotlin.math.floor(this) && !isInfinite()) toLong().toString() else toString()
}

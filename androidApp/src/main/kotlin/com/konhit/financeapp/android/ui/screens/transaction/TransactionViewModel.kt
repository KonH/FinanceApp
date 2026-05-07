package com.konhit.financeapp.android.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.model.*
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TransactionFormState(
    val type: TransactionType = TransactionType.WITHDRAWAL,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categId: Long? = null,
    val transDate: String = MmexDateFormat.formatTransDate(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ),
    val amount: String = "",
    val toAmount: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class TransactionViewModel(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val syncCoordinator: SyncCoordinator,
    private val dbHolder: DatabaseHolder,
    private val initialAccountId: Long?,
    private val editTransId: Long?
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionFormState())
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepo.getAll()
            val categories = categoryRepo.getAll()
            _state.update { it.copy(accounts = accounts, categories = categories) }

            if (editTransId != null) {
                loadForEdit(editTransId)
            } else {
                _state.update { it.copy(accountId = initialAccountId ?: accounts.firstOrNull()?.id) }
            }
        }
    }

    private suspend fun loadForEdit(transId: Long) {
        val tx = transactionRepo.getById(transId) ?: return
        _state.update {
            it.copy(
                type       = tx.type,
                accountId  = tx.accountId,
                toAccountId = tx.toAccountId.takeIf { id -> id != -1L },
                categId    = tx.categId,
                transDate  = tx.transDate,
                amount     = tx.transAmount.toDisplayString(),
                toAmount   = tx.toTransAmount.toDisplayString(),
                notes      = tx.notes ?: ""
            )
        }
    }

    fun onTypeChanged(type: TransactionType)      = _state.update { it.copy(type = type) }
    fun onAccountChanged(id: Long)               = _state.update { it.copy(accountId = id) }
    fun onToAccountChanged(id: Long)             = _state.update { it.copy(toAccountId = id) }
    fun onCategorySelected(category: Category)   = _state.update { it.copy(categId = category.id) }
    fun onDateChanged(date: String)              = _state.update { it.copy(transDate = date) }
    fun onAmountChanged(amount: String)          = _state.update { it.copy(amount = amount) }
    fun onToAmountChanged(amount: String)        = _state.update { it.copy(toAmount = amount) }
    fun onNotesChanged(notes: String)            = _state.update { it.copy(notes = notes) }

    fun onSave() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val s = _state.value
                val accountId = s.accountId ?: error("Account required")
                val categId   = s.categId   ?: error("Category required")
                val amount    = s.amount.toDoubleOrNull() ?: error("Invalid amount")
                val toAmount  = s.toAmount.toDoubleOrNull() ?: 0.0
                val toAccountId = if (s.type == TransactionType.TRANSFER) {
                    s.toAccountId ?: error("To-account required for transfer")
                } else -1L

                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val tx = Transaction(
                    transId         = editTransId ?: (System.currentTimeMillis() * 1000L),
                    accountId       = accountId,
                    toAccountId     = toAccountId,
                    payeeId         = dbHolder.defaultPayeeId,
                    type            = s.type,
                    transAmount     = amount,
                    toTransAmount   = if (s.type == TransactionType.TRANSFER) toAmount else 0.0,
                    categId         = categId,
                    transDate       = s.transDate,
                    lastUpdatedTime = MmexDateFormat.formatLastUpdated(now),
                    notes           = s.notes.ifBlank { null }
                )

                if (editTransId != null) transactionRepo.update(tx)
                else transactionRepo.insert(tx)

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

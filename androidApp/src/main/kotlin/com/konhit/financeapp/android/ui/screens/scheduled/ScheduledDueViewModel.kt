package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.ScheduledTransaction
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.domain.usecase.ProcessDueScheduledUseCase
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DueDialogInfo(
    val scheduled: ScheduledTransaction,
    val accountName: String,
    val toAccountName: String?,
    val categoryPath: String
)

data class ScheduledDueState(
    val current: DueDialogInfo? = null,
    val busy: Boolean = false
)

class ScheduledDueViewModel(
    private val processDue: ProcessDueScheduledUseCase,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduledDueState())
    val state: StateFlow<ScheduledDueState> = _state.asStateFlow()

    private var checking = false

    fun checkDue() {
        if (checking || _state.value.current != null || _state.value.busy) return
        viewModelScope.launch {
            checking = true
            try {
                showNextDue()
            } finally {
                checking = false
            }
        }
    }

    private suspend fun showNextDue() {
        val due = processDue.listDueToday()
        val next = due.firstOrNull() ?: run {
            _state.update { it.copy(current = null) }
            return
        }
        _state.update { it.copy(current = toInfo(next)) }
    }

    private suspend fun toInfo(s: ScheduledTransaction): DueDialogInfo {
        val accounts = accountRepo.getAll().associate { it.id to it.name }
        val categories = categoryRepo.getAll()
        return DueDialogInfo(
            scheduled = s,
            accountName = accounts[s.accountId] ?: "",
            toAccountName = s.toAccountId.takeIf { it != -1L }?.let { accounts[it] },
            categoryPath = s.categId?.let { categories.buildPath(it) } ?: ""
        )
    }

    fun onApprove() = act { processDue.approve(it) }

    fun onCancelOnce() = act { processDue.cancelOnce(it) }

    fun onDeleteScheduled() = act {
        processDue.delete(it)
        null
    }

    private fun act(block: suspend (ScheduledTransaction) -> ScheduledTransaction?) {
        val current = _state.value.current?.scheduled ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block(current)
                syncCoordinator.uploadCurrent()
                val next = processDue.listDueToday()
                _state.update {
                    it.copy(
                        busy = false,
                        current = next.firstOrNull()?.let { s -> toInfo(s) }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

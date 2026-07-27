package com.konhit.financeapp.android.ui.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.ScheduledTransaction
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.ScheduledTransactionRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduledListState(
    val items: List<ScheduledListItem> = emptyList(),
    val isReadOnly: Boolean = false
)

data class ScheduledListItem(
    val scheduled: ScheduledTransaction,
    val accountName: String,
    val categoryPath: String
)

class ScheduledListViewModel(
    private val scheduledRepo: ScheduledTransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduledListState())
    val state: StateFlow<ScheduledListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.observeAccessMode().collect { mode ->
                _state.update { it.copy(isReadOnly = mode == AccessMode.READ_ONLY) }
            }
        }
        viewModelScope.launch {
            scheduledRepo.observeAllOrdered().collect { list ->
                val accounts = accountRepo.getAll().associate { it.id to it.name }
                val categories = categoryRepo.getAll()
                _state.update {
                    it.copy(
                        items = list.map { s ->
                            ScheduledListItem(
                                scheduled = s,
                                accountName = accounts[s.accountId] ?: "",
                                categoryPath = s.categId?.let { id -> categories.buildPath(id) } ?: ""
                            )
                        }
                    )
                }
            }
        }
    }

    fun onDelete(bdId: Long) {
        viewModelScope.launch {
            scheduledRepo.delete(bdId)
            syncCoordinator.uploadCurrent()
        }
    }
}

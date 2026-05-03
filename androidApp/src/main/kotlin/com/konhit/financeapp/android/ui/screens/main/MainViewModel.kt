package com.konhit.financeapp.android.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Account
import com.konhit.financeapp.domain.model.AccessMode
import com.konhit.financeapp.domain.model.SyncState
import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainState(
    val accounts: List<Account> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Transaction> = emptyList(),
    val isSearching: Boolean = false,
    val syncState: SyncState = SyncState.Idle,
    val isReadOnly: Boolean = false
)

@OptIn(FlowPreview::class)
class MainViewModel(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val settings: SettingsRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            accountRepo.observeAll().collect { accounts ->
                _state.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            settings.observeAccessMode().collect { mode ->
                _state.update { it.copy(isReadOnly = mode == AccessMode.READ_ONLY) }
            }
        }
        viewModelScope.launch {
            syncCoordinator.syncState.collect { syncState ->
                _state.update { it.copy(syncState = syncState) }
            }
        }
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
                    } else {
                        _state.update { it.copy(isSearching = true) }
                        val results = transactionRepo.search(query)
                        _state.update { it.copy(searchResults = results, isSearching = false) }
                    }
                }
        }
        loadAccounts()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchQuery.value = query
    }

    fun onSyncClick() {
        viewModelScope.launch { syncCoordinator.uploadCurrent() }
    }

    fun onConflictKeepLocal() {
        viewModelScope.launch { syncCoordinator.resolveConflictKeepLocal() }
    }

    fun onConflictKeepRemote() {
        viewModelScope.launch {
            val fileId = settings.getDriveFileId() ?: return@launch
            val mode = settings.getAccessMode()
            syncCoordinator.resolveConflictKeepRemote(fileId, mode)
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val accounts = accountRepo.getAll()
            _state.update { it.copy(accounts = accounts) }
        }
    }
}

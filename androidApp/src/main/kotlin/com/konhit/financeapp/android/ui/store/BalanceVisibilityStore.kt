package com.konhit.financeapp.android.ui.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BalanceVisibilityStore {
    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    fun toggle() {
        _isVisible.value = !_isVisible.value
    }
}

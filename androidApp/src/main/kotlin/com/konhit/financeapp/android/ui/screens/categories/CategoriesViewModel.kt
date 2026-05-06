package com.konhit.financeapp.android.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.drive.SyncCoordinator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryDialogState(
    val id: Long? = null,
    val name: String = "",
    val parentId: Long = -1L
)

data class CategoriesState(
    val categories: List<Category> = emptyList(),
    val dialog: CategoryDialogState? = null
)

class CategoriesViewModel(
    private val categoryRepo: CategoryRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepo.observeAll().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
    }

    fun onAddClick() = _state.update { it.copy(dialog = CategoryDialogState()) }

    fun onEditClick(category: Category) = _state.update {
        it.copy(
            dialog = CategoryDialogState(
                id = category.id,
                name = category.name,
                parentId = category.parentId
            )
        )
    }

    fun onDialogNameChange(name: String) = _state.update { it.copy(dialog = it.dialog?.copy(name = name)) }
    fun onDialogParentChange(parentId: Long) = _state.update { it.copy(dialog = it.dialog?.copy(parentId = parentId)) }
    fun onDialogDismiss() = _state.update { it.copy(dialog = null) }

    fun onDialogSave() {
        val dialog = _state.value.dialog ?: return
        viewModelScope.launch {
            if (dialog.id == null) {
                categoryRepo.insert(Category(System.currentTimeMillis() * 1000L, dialog.name, dialog.parentId))
            } else {
                categoryRepo.update(Category(dialog.id, dialog.name, dialog.parentId))
            }
            syncCoordinator.uploadCurrent()
            _state.update { it.copy(dialog = null) }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            if (!categoryRepo.hasTransactions(id)) {
                categoryRepo.delete(id)
                syncCoordinator.uploadCurrent()
            }
        }
    }
}

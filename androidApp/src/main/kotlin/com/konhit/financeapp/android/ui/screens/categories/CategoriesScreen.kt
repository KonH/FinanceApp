package com.konhit.financeapp.android.ui.screens.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.buildPath
import com.konhit.financeapp.domain.model.toTree
import com.konhit.financeapp.domain.model.flattenWithDepth
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val viewModel: CategoriesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    val flatCategories = remember(state.categories) {
        state.categories.toTree().flatMap { it.flattenWithDepth() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Default.Add, "Add category")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(flatCategories, key = { it.first.category.id }) { (node, depth) ->
                CategoryItem(
                    category = node.category,
                    depth = depth,
                    onEdit = { viewModel.onEditClick(node.category) },
                    onDelete = { viewModel.onDelete(node.category.id) }
                )
                HorizontalDivider()
            }
        }
    }

    state.dialog?.let { dialog ->
        CategoryDialog(
            dialog = dialog,
            categories = state.categories,
            onNameChange = viewModel::onDialogNameChange,
            onParentChange = viewModel::onDialogParentChange,
            onDismiss = viewModel::onDialogDismiss,
            onSave = viewModel::onDialogSave
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    depth: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(category.name) },
        modifier = Modifier.padding(start = (depth * 16).dp),
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    dialog: CategoryDialogState,
    categories: List<Category>,
    onNameChange: (String) -> Unit,
    onParentChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var parentExpanded by remember { mutableStateOf(false) }

    val parentOptions = remember(categories, dialog.id) {
        listOf(-1L to "None") +
            categories
                .filter { it.id != dialog.id }
                .map { it.id to categories.buildPath(it.id) }
                .sortedBy { it.second }
    }
    val selectedParentLabel = parentOptions
        .find { it.first == dialog.parentId }
        ?.second
        ?: "None"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog.id == null) "Add Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = parentExpanded,
                    onExpandedChange = { parentExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedParentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Parent") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parentExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = parentExpanded,
                        onDismissRequest = { parentExpanded = false }
                    ) {
                        parentOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onParentChange(id); parentExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = dialog.name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

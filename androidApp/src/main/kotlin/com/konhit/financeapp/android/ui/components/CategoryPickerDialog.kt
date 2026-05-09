package com.konhit.financeapp.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.CategoryNode
import com.konhit.financeapp.domain.model.flattenWithDepth
import com.konhit.financeapp.domain.model.toTree

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    val roots = remember(categories) { categories.toTree() }
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }

    val flatList = remember(roots, expandedIds, searchQuery) {
        if (searchQuery.isBlank()) {
            roots.flatMap { root -> root.flattenFiltered(expandedIds) }
        } else {
            val q = searchQuery.trim().lowercase()
            categories
                .filter { it.name.lowercase().contains(q) }
                .map { cat -> roots.findNode(cat.id)!! to 0 }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select category") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(flatList, key = { it.first.category.id }) { (node, depth) ->
                        CategoryRow(
                            node = node,
                            depth = depth,
                            isExpanded = node.category.id in expandedIds,
                            showToggle = searchQuery.isBlank(),
                            onToggle = {
                                expandedIds = if (node.category.id in expandedIds)
                                    expandedIds - node.category.id
                                else
                                    expandedIds + node.category.id
                            },
                            onSelect = { onSelect(node.category) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryRow(
    node: CategoryNode,
    depth: Int,
    isExpanded: Boolean,
    showToggle: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val hasChildren = node.children.isNotEmpty() && showToggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasChildren) onToggle() else onSelect() }
            .padding(start = (16 + depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(node.category.name)
    }
}

private fun List<CategoryNode>.findNode(id: Long): CategoryNode? {
    for (node in this) {
        if (node.category.id == id) return node
        node.children.findNode(id)?.let { return it }
    }
    return null
}

private fun CategoryNode.flattenFiltered(expandedIds: Set<Long>): List<Pair<CategoryNode, Int>> {
    val result = mutableListOf(this to 0)
    if (category.id in expandedIds) {
        children.forEach { child ->
            result.addAll(child.flattenFilteredInner(expandedIds, depth = 1))
        }
    }
    return result
}

private fun CategoryNode.flattenFilteredInner(expandedIds: Set<Long>, depth: Int): List<Pair<CategoryNode, Int>> {
    val result = mutableListOf(this to depth)
    if (category.id in expandedIds) {
        children.forEach { child ->
            result.addAll(child.flattenFilteredInner(expandedIds, depth + 1))
        }
    }
    return result
}

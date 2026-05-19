package com.konhit.financeapp.domain.model

data class Category(
    val id: Long,
    val name: String,
    val parentId: Long
)

data class CategoryNode(
    val category: Category,
    val children: List<CategoryNode> = emptyList()
)

fun List<Category>.toTree(): List<CategoryNode> {
    val byParent = groupBy { it.parentId }
    fun buildNode(cat: Category): CategoryNode =
        CategoryNode(cat, byParent[cat.id]?.map { buildNode(it) } ?: emptyList())
    return (byParent[-1L] ?: emptyList()).map { buildNode(it) }
}

fun CategoryNode.flattenWithDepth(depth: Int = 0): List<Pair<CategoryNode, Int>> =
    listOf(this to depth) + children.flatMap { it.flattenWithDepth(depth + 1) }

fun List<Category>.buildPath(id: Long): String {
    val byId = associateBy { it.id }
    val parts = mutableListOf<String>()
    var cur = byId[id]
    while (cur != null) {
        parts.add(0, cur.name)
        cur = if (cur.parentId == -1L) null else byId[cur.parentId]
    }
    return parts.joinToString("/")
}

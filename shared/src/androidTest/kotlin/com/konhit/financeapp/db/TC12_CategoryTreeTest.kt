package com.konhit.financeapp.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.konhit.financeapp.domain.model.Category
import com.konhit.financeapp.domain.model.flattenWithDepth
import com.konhit.financeapp.domain.model.toTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that parent→child relationships read from CATEGORY_V1 survive the
 * open path and are correctly represented by toTree().
 */
@RunWith(AndroidJUnit4::class)
class TC12_CategoryTreeTest : BaseDbTest() {

    @Test
    fun openedDb_subCategoriesAreNestedUnderParent() {
        val allCategories = database.categoryQueries.selectAll().executeAsList()
            .map { Category(it.CATEGID, it.CATEGNAME, it.PARENTID) }

        val subCategory = allCategories.firstOrNull { it.parentId != -1L }
        assertNotNull(
            "example.mmb has no subcategories — can't verify hierarchy",
            subCategory
        )
        subCategory!!

        val tree = allCategories.toTree()
        val flatWithDepth = tree.flatMap { it.flattenWithDepth() }

        val subEntry = flatWithDepth.find { (node, _) -> node.category.id == subCategory.id }
        assertNotNull("Subcategory '${subCategory.name}' not found in tree at all", subEntry)
        assertTrue(
            "Subcategory '${subCategory.name}' expected depth >= 1 but was ${subEntry!!.second}",
            subEntry.second >= 1
        )
    }

    @Test
    fun insertedHierarchy_isCorrectlyNestedInTree() {
        val parentId = System.currentTimeMillis() * 1000L
        val childId  = parentId + 1

        database.categoryQueries.insert(parentId, "TestParent", -1L)
        database.categoryQueries.insert(childId,  "TestChild",  parentId)

        val allCategories = database.categoryQueries.selectAll().executeAsList()
            .map { Category(it.CATEGID, it.CATEGNAME, it.PARENTID) }

        val tree = allCategories.toTree()
        val parentNode = tree.find { it.category.id == parentId }
        assertNotNull("Parent category not found in tree roots", parentNode)
        assertEquals(
            "Parent should have exactly 1 child",
            1,
            parentNode!!.children.size
        )
        assertEquals(
            "Child category id should match",
            childId,
            parentNode.children[0].category.id
        )

        val flatWithDepth = tree.flatMap { it.flattenWithDepth() }
        val childEntry = flatWithDepth.find { (node, _) -> node.category.id == childId }
        assertNotNull("Child not found in flat list", childEntry)
        assertEquals(
            "Child should be at depth 1",
            1,
            childEntry!!.second
        )
    }
}

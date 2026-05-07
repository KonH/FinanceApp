package com.konhit.financeapp.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TC01_OpenExistingTest : BaseDbTest() {

    @Test
    fun accountList_loads16Accounts() {
        val count = database.accountListQueries.count().executeAsOne()
        assertEquals(16L, count)
    }

    @Test
    fun infoTable_dataVersion_is3() {
        val value = database.infoTableQueries.selectByKey("DATAVERSION").executeAsOneOrNull()
        assertEquals("3", value)
    }

    @Test
    fun categoryTree_loads119Entries() {
        val count = database.categoryQueries.count().executeAsOne()
        assertEquals(119L, count)
    }

    @Test
    fun raifRsd_hasExpectedInitialBalance() {
        val raifRsdId = 1748881720210000L
        val account = database.accountListQueries.selectById(raifRsdId).executeAsOneOrNull()
        assertNotNull("Raif RSD account not found", account)
        assertEquals(284889.0, account!!.INITIALBAL, 0.01)
        assertEquals(121L, account.CURRENCYID)
    }

    @Test
    fun rootCategories_haveMinus1ParentId() {
        val allCategories = database.categoryQueries.selectAll().executeAsList()
        val rootCategories = allCategories.filter { it.PARENTID == -1L }
        assert(rootCategories.isNotEmpty()) { "No root categories found" }
        rootCategories.forEach { cat ->
            assertEquals(-1L, cat.PARENTID)
        }
    }

    @Test
    fun categoryHierarchy_subCategoriesExistAndHaveValidParent() {
        val allCategories = database.categoryQueries.selectAll().executeAsList()
        val ids = allCategories.map { it.CATEGID }.toSet()
        val subCategories = allCategories.filter { it.PARENTID != -1L }

        assert(subCategories.isNotEmpty()) {
            "example.mmb has no subcategories — hierarchy has never been tested"
        }
        subCategories.forEach { sub ->
            assert(sub.PARENTID in ids) {
                "Subcategory '${sub.CATEGNAME}' has PARENTID=${sub.PARENTID} which does not match any CATEGID"
            }
        }
    }
}

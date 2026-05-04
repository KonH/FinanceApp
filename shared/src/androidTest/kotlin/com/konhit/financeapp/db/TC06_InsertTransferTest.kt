package com.konhit.financeapp.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TC06_InsertTransferTest : BaseDbTest() {

    private val raifRsdId = 1748881720210000L
    private val raifEurId = 1748881873924000L

    @Test
    fun insertTransfer_allDefaultsCorrect() {
        val transId = System.currentTimeMillis() * 1000L

        database.transactionQueries.insert(
            transId         = transId,
            accountId       = raifRsdId,
            toAccountId     = raifEurId,
            payeeId         = defaultPayeeId,
            transCode       = "Transfer",
            transAmount     = 117000.0,
            toTransAmount   = 1000.0,
            notes           = null,
            categId         = null,
            transDate       = "2026-05-04T00:00:00",
            lastUpdatedTime = "2026-05-04T12:00:00"
        )

        val row = database.transactionQueries.selectById(transId).executeAsOneOrNull()
        assertNotNull("Inserted row not found", row)
        row!!

        assertEquals("Transfer", row.TRANSCODE)
        assertEquals(raifRsdId, row.ACCOUNTID)
        assertEquals(raifEurId, row.TOACCOUNTID)
        assertEquals(117000.0, row.TRANSAMOUNT, 0.001)
        assertEquals(1000.0, row.TOTRANSAMOUNT, 0.001)
        assertEquals("", row.STATUS)
        assertEquals(-1L, row.FOLLOWUPID)
        assertEquals(-1L, row.COLOR)
    }

    @Test
    fun insertTransfer_balancesAdjustCorrectly() {
        val rsdBefore = computeBalance(raifRsdId)
        val eurBefore = computeBalance(raifEurId)

        database.transactionQueries.insert(
            transId         = System.currentTimeMillis() * 1000L,
            accountId       = raifRsdId,
            toAccountId     = raifEurId,
            payeeId         = defaultPayeeId,
            transCode       = "Transfer",
            transAmount     = 117000.0,
            toTransAmount   = 1000.0,
            notes           = null,
            categId         = null,
            transDate       = "2026-05-04T00:00:00",
            lastUpdatedTime = "2026-05-04T12:00:00"
        )

        assertEquals(rsdBefore - 117000.0, computeBalance(raifRsdId), 0.001)
        assertEquals(eurBefore + 1000.0, computeBalance(raifEurId), 0.001)
    }

    private fun computeBalance(accountId: Long): Double {
        val c = database.transactionQueries.balanceComponents(accountId).executeAsOne()
        return c.initialBal +
            (c.totalDeposits ?: 0.0) -
            (c.totalWithdrawals ?: 0.0) +
            (c.transfersIn ?: 0.0) -
            (c.transfersOut ?: 0.0)
    }
}

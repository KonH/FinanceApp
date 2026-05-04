package com.konhit.financeapp.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TC05_InsertDepositTest : BaseDbTest() {

    private val raifRsdId = 1748881720210000L

    @Test
    fun insertDeposit_allDefaultsCorrect() {
        val transId = System.currentTimeMillis() * 1000L

        database.transactionQueries.insert(
            transId         = transId,
            accountId       = raifRsdId,
            toAccountId     = -1L,
            payeeId         = defaultPayeeId,
            transCode       = "Deposit",
            transAmount     = 50000.0,
            toTransAmount   = 0.0,
            notes           = "test deposit",
            categId         = 10L,
            transDate       = "2026-05-04T00:00:00",
            lastUpdatedTime = "2026-05-04T12:00:00"
        )

        val row = database.transactionQueries.selectById(transId).executeAsOneOrNull()
        assertNotNull("Inserted row not found", row)
        row!!

        assertEquals("Deposit", row.TRANSCODE)
        assertEquals(50000.0, row.TRANSAMOUNT, 0.001)
        assertEquals(0.0, row.TOTRANSAMOUNT, 0.001)
        assertEquals(-1L, row.TOACCOUNTID)
        assertEquals("", row.STATUS)
        assertEquals(-1L, row.FOLLOWUPID)
        assertEquals(-1L, row.COLOR)
        assertTrue("PAYEEID must be valid (> 0)", row.PAYEEID > 0)
        assertEquals(16, row.TRANSID.toString().length)
    }

    @Test
    fun insertDeposit_balanceIncreases() {
        val balanceBefore = computeBalance(raifRsdId)

        database.transactionQueries.insert(
            transId         = System.currentTimeMillis() * 1000L,
            accountId       = raifRsdId,
            toAccountId     = -1L,
            payeeId         = defaultPayeeId,
            transCode       = "Deposit",
            transAmount     = 50000.0,
            toTransAmount   = 0.0,
            notes           = "test deposit",
            categId         = 10L,
            transDate       = "2026-05-04T00:00:00",
            lastUpdatedTime = "2026-05-04T12:00:00"
        )

        val balanceAfter = computeBalance(raifRsdId)
        assertEquals(balanceBefore + 50000.0, balanceAfter, 0.001)
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

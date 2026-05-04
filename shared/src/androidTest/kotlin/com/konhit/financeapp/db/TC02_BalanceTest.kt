package com.konhit.financeapp.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TC02_BalanceTest : BaseDbTest() {

    @Test
    fun raifRsd_balance_approx334857() {
        val raifRsdId = 1748881720210000L
        val c = database.transactionQueries.balanceComponents(raifRsdId).executeAsOne()
        val balance = c.initialBal +
            (c.totalDeposits ?: 0.0) -
            (c.totalWithdrawals ?: 0.0) +
            (c.transfersIn ?: 0.0) -
            (c.transfersOut ?: 0.0)
        assertEquals(334857.57, balance, 0.01)
    }

    @Test
    fun raifEur_balance_computedSeparately() {
        val raifRsdId = 1748881720210000L
        val raifEurId = 1748881873924000L

        val cRsd = database.transactionQueries.balanceComponents(raifRsdId).executeAsOne()
        val cEur = database.transactionQueries.balanceComponents(raifEurId).executeAsOne()

        val balanceRsd = cRsd.initialBal +
            (cRsd.totalDeposits ?: 0.0) -
            (cRsd.totalWithdrawals ?: 0.0) +
            (cRsd.transfersIn ?: 0.0) -
            (cRsd.transfersOut ?: 0.0)
        val balanceEur = cEur.initialBal +
            (cEur.totalDeposits ?: 0.0) -
            (cEur.totalWithdrawals ?: 0.0) +
            (cEur.transfersIn ?: 0.0) -
            (cEur.transfersOut ?: 0.0)

        assert(balanceRsd != balanceEur) { "RSD and EUR balances should be independent" }
    }
}

package com.konhit.financeapp.domain.rates

import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExchangeRatesTest {

    private val today = "2026-09-22"

    @Test
    fun plan_groupsByMonth_withLookback_andClampsToToday() {
        val needs = listOf(
            RateNeed("RSD", "2026-08-03"),
            RateNeed("USD", "2026-08-20"),
            RateNeed("EUR", "2026-08-20"),
            RateNeed("RSD", "2026-12-01")
        )
        val plan = ExchangeRates.plan(RateTable(), needs, today)
        assertEquals(
            listOf(
                RateFetch("2026-08", "2026-07-25", "2026-08-31", listOf("RSD", "USD")),
                RateFetch("2026-09", "2026-08-25", today, listOf("RSD"))
            ),
            plan
        )
    }

    @Test
    fun plan_skipsCoveredAndUnsupported() {
        val fetch = RateFetch("2026-08", "2026-07-25", "2026-08-31", listOf("RSD"))
        val table = ExchangeRates.merge(RateTable(), fetch, emptyList())
        val needs = listOf(RateNeed("RSD", "2026-08-10"), RateNeed("XYZ", "2026-08-10"))
        assertTrue(ExchangeRates.plan(table, needs, today, supported = setOf("RSD", "USD")).isEmpty())
    }

    @Test
    fun plan_refetchesMonthCoveredOnlyUpToAnEarlierDay() {
        val fetch = RateFetch("2026-09", "2026-08-25", "2026-09-10", listOf("RSD"))
        val table = ExchangeRates.merge(RateTable(), fetch, emptyList())
        assertEquals(1, ExchangeRates.plan(table, listOf(RateNeed("RSD", "2026-09-15")), today).size)
    }

    @Test
    fun convert_crossRateThroughPivot_withWeekendFallback() {
        val fetch = RateFetch("2019-05", "2019-04-24", "2019-05-31", listOf("RSD", "USD"))
        val table = ExchangeRates.merge(
            RateTable(),
            fetch,
            listOf(RateRow("2019-05-03", "RSD", 118.0), RateRow("2019-05-03", "USD", 1.18))
        )
        // Saturday 2019-05-04 has no row: Friday's rates are used.
        assertEquals(11.8, ExchangeRates.convert(table, 1180.0, "RSD", "USD", "2019-05-04")!!, 1e-9)
        assertEquals(1.0, ExchangeRates.convert(table, 118.0, "RSD", "EUR", "2019-05-03")!!, 1e-9)
        assertNull(ExchangeRates.convert(table, 1.0, "RSD", "EUR", "2019-05-20"))
    }

    @Test
    fun filteredBalance_transfersCountOnlyForOneAccount() {
        val txs = listOf(
            tx(1, TransactionType.DEPOSIT, accountId = 1, amount = 236.0),
            tx(2, TransactionType.WITHDRAWAL, accountId = 2, amount = 5.0),
            tx(3, TransactionType.TRANSFER, accountId = 2, toAccountId = 1, amount = 1.0, toAmount = 118.0)
        )
        val codes = mapOf(1L to "RSD", 2L to "EUR")
        val table = ExchangeRates.merge(
            RateTable(),
            RateFetch("2019-05", "2019-04-24", "2019-05-31", listOf("RSD")),
            listOf(RateRow("2019-05-03", "RSD", 118.0))
        )

        val all = FilteredBalance.compute(FilteredBalance.legs(txs, null, codes, today), "EUR", table)
        assertNotNull(all.balance)
        assertEquals(2.0, all.balance!!.income, 1e-9)
        assertEquals(5.0, all.balance!!.expense, 1e-9)
        assertEquals(-3.0, all.balance!!.net, 1e-9)

        val rsdOnly = FilteredBalance.compute(FilteredBalance.legs(txs, 1L, codes, today), "RSD", table)
        assertEquals(236.0 + 118.0 - 5.0 * 118.0, rsdOnly.balance!!.net, 1e-9)
    }

    @Test
    fun filteredBalance_reportsMissingCurrency() {
        val legs = listOf(BalanceLeg("USD", "2019-05-03", 10.0))
        val result = FilteredBalance.compute(legs, "EUR", RateTable())
        assertNull(result.balance)
        assertEquals(listOf("USD"), result.missingCodes)
        assertEquals(setOf(RateNeed("USD", "2019-05-03"), RateNeed("EUR", "2019-05-03")), FilteredBalance.needs(legs, "EUR"))
    }

    @Test
    fun convertSum_convertsAccountTotals_andReportsMissingRates() {
        val table = ExchangeRates.merge(
            RateTable(),
            RateFetch("2026-09", "2026-08-25", "2026-09-23", listOf("RSD", "USD")),
            listOf(RateRow("2026-09-22", "RSD", 117.5), RateRow("2026-09-22", "USD", 1.15))
        )
        val amounts = listOf("RSD" to 117_500.0, "EUR" to 100.0, "USD" to 115.0)
        // 23rd has no row yet: the 22nd's rates are used.
        assertEquals(1200.0, ExchangeRates.convertSum(table, amounts, "EUR", "2026-09-23")!!, 1e-9)
        assertEquals(1200.0 * 117.5, ExchangeRates.convertSum(table, amounts, "RSD", "2026-09-23")!!, 1e-6)
        assertNull(ExchangeRates.convertSum(table, amounts + ("GBP" to 1.0), "EUR", "2026-09-23"))
        assertEquals(listOf("GBP"), ExchangeRates.missingCodes(table, listOf("GBP", "RSD", "EUR"), "EUR", "2026-09-23"))
        assertTrue(ExchangeRates.missingCodes(RateTable(), listOf("GBP"), "GBP", "2026-09-23").isEmpty())
        assertEquals(listOf("GBP"), ExchangeRates.missingCodes(RateTable(), listOf("EUR"), "GBP", "2026-09-23"))
    }

    private fun tx(
        id: Long,
        type: TransactionType,
        accountId: Long,
        amount: Double,
        toAccountId: Long = -1,
        toAmount: Double = 0.0
    ) = Transaction(
        transId = id,
        accountId = accountId,
        toAccountId = toAccountId,
        payeeId = 1,
        type = type,
        transAmount = amount,
        toTransAmount = toAmount,
        categId = null,
        transDate = "2019-05-03T00:00:00",
        lastUpdatedTime = null,
        notes = null
    )
}

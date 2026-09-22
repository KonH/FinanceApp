package com.konhit.financeapp.domain.rates

import com.konhit.financeapp.domain.model.Transaction
import com.konhit.financeapp.domain.model.TransactionType

/** One signed movement of money, in the currency ([code]) of the account it touched. */
data class BalanceLeg(val code: String, val date: String, val amount: Double)

data class BaseCurrencyBalance(val income: Double, val expense: Double) {
    val net: Double get() = income - expense
}

/** [balance] is null while any rate is missing; [missingCodes] then names the currencies without one. */
data class BaseBalanceResult(val balance: BaseCurrencyBalance?, val missingCodes: List<String>)

/** Balance of a filtered transaction list converted into one base currency at each transaction's date. */
object FilteredBalance {

    /**
     * Deposits add, withdrawals subtract. Transfers only count when the list is
     * limited to one account ([accountFilterId]): incoming adds the destination
     * amount, outgoing subtracts the source amount — the account balance formula.
     * Without an account filter a transfer just moves money inside the set.
     * Dates after [today] are valued at [today]'s rate.
     */
    fun legs(
        transactions: List<Transaction>,
        accountFilterId: Long?,
        accountCodes: Map<Long, String>,
        today: String
    ): List<BalanceLeg> = transactions.flatMap { tx ->
        val date = minOf(tx.transDate.substringBefore('T'), today)
        fun leg(accountId: Long, amount: Double) =
            accountCodes[accountId]?.let { BalanceLeg(it, date, amount) }
        when (tx.type) {
            TransactionType.DEPOSIT    -> listOfNotNull(leg(tx.accountId, tx.transAmount))
            TransactionType.WITHDRAWAL -> listOfNotNull(leg(tx.accountId, -tx.transAmount))
            TransactionType.TRANSFER   -> when (accountFilterId) {
                null -> emptyList()
                tx.toAccountId -> listOfNotNull(leg(tx.toAccountId, tx.toTransAmount))
                tx.accountId -> listOfNotNull(leg(tx.accountId, -tx.transAmount))
                else -> emptyList()
            }
        }
    }

    /** Rates needed to convert [legs] into [baseCode]: the leg's currency and the base, per day. */
    fun needs(legs: List<BalanceLeg>, baseCode: String): Set<RateNeed> =
        legs.filter { it.code != baseCode }
            .flatMap { listOf(RateNeed(it.code, it.date), RateNeed(baseCode, it.date)) }
            .toSet()

    fun compute(legs: List<BalanceLeg>, baseCode: String, table: RateTable): BaseBalanceResult {
        var income = 0.0
        var expense = 0.0
        val missing = mutableSetOf<String>()
        for (leg in legs) {
            val converted = ExchangeRates.convert(table, leg.amount, leg.code, baseCode, leg.date)
            if (converted == null) {
                missing += if (ExchangeRates.pivotRate(table, leg.code, leg.date) == null) leg.code else baseCode
                continue
            }
            if (converted >= 0) income += converted else expense -= converted
        }
        return if (missing.isEmpty()) {
            BaseBalanceResult(BaseCurrencyBalance(income, expense), emptyList())
        } else {
            BaseBalanceResult(null, missing.sorted())
        }
    }
}

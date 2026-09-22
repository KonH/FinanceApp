package com.konhit.financeapp.domain.rates

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Daily exchange rates cached against a single pivot currency ([ExchangeRates.PIVOT]):
 * `rates[code][date]` is how many units of `code` one EUR bought on that day.
 * Converting A → B is `rate(B) / rate(A)`, so picking another target currency
 * never needs a new download.
 *
 * `coverage[code][month]` (`YYYY-MM`) records the last day of that month already
 * requested for `code`. Days without a published rate (weekends, holidays) fall
 * back to the closest earlier day within [ExchangeRates.LOOKBACK_DAYS].
 */
data class RateTable(
    val rates: Map<String, Map<String, Double>> = emptyMap(),
    val coverage: Map<String, Map<String, String>> = emptyMap()
)

/** A rate the caller needs: [code] on [date] (`YYYY-MM-DD`). */
data class RateNeed(val code: String, val date: String)

/** One download: every day in [from]..[to] for [codes]; it completes [month] up to [to]. */
data class RateFetch(val month: String, val from: String, val to: String, val codes: List<String>)

data class RateRow(val date: String, val code: String, val rate: Double)

object ExchangeRates {
    const val PIVOT = "EUR"
    const val LOOKBACK_DAYS = 7

    /** ISO code of an MMEX currency (`CURRENCY_SYMBOL`), falling back to its name. */
    fun codeOf(currencySymbol: String?, name: String): String =
        currencySymbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: name

    /**
     * Downloads still missing for [needs]: one per calendar month, carrying every
     * code that month lacks. Dates after [today] use [today]'s rate. Codes outside
     * [supported] (when known) are left out — they can never be fetched.
     */
    fun plan(
        table: RateTable,
        needs: Collection<RateNeed>,
        today: String,
        supported: Set<String>? = null
    ): List<RateFetch> {
        val byMonth = mutableMapOf<String, MutableSet<String>>()
        for (need in needs) {
            if (need.code == PIVOT) continue
            if (supported != null && need.code !in supported) continue
            val date = minOf(need.date, today)
            val month = date.substring(0, 7)
            val covered = table.coverage[need.code]?.get(month)
            if (covered == null || covered < date) byMonth.getOrPut(month) { mutableSetOf() } += need.code
        }
        return byMonth.keys.sorted().map { month ->
            val first = LocalDate.parse("$month-01")
            val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).toString()
            RateFetch(
                month = month,
                from = first.minus(DatePeriod(days = LOOKBACK_DAYS)).toString(),
                to = minOf(last, today),
                codes = byMonth.getValue(month).sorted()
            )
        }
    }

    /** Folds a finished [fetch] into [table]. Requested codes count as covered even when no rows came back. */
    fun merge(table: RateTable, fetch: RateFetch, rows: List<RateRow>): RateTable {
        val rates = table.rates.toMutableMap()
        for ((code, codeRows) in rows.groupBy { it.code }) {
            rates[code] = (rates[code].orEmpty()) + codeRows.associate { it.date to it.rate }
        }
        val coverage = table.coverage.toMutableMap()
        for (code in fetch.codes) {
            val months = coverage[code].orEmpty()
            val current = months[fetch.month]
            if (current == null || current < fetch.to) coverage[code] = months + (fetch.month to fetch.to)
        }
        return RateTable(rates, coverage)
    }

    /** Units of [code] per one [PIVOT] on [date], or on the closest earlier day within [LOOKBACK_DAYS]. */
    fun pivotRate(table: RateTable, code: String, date: String): Double? {
        if (code == PIVOT) return 1.0
        val byDate = table.rates[code] ?: return null
        var day = LocalDate.parse(date)
        repeat(LOOKBACK_DAYS + 1) {
            byDate[day.toString()]?.let { return it }
            day = day.minus(DatePeriod(days = 1))
        }
        return null
    }

    /** [amount] of [from] expressed in [to] at [date]'s rate; null when a rate is not cached. */
    fun convert(table: RateTable, amount: Double, from: String, to: String, date: String): Double? {
        if (from == to) return amount
        val fromRate = pivotRate(table, from, date) ?: return null
        val toRate = pivotRate(table, to, date) ?: return null
        return amount * toRate / fromRate
    }
}

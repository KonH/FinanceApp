package com.konhit.financeapp.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object ScheduleDateMath {

    private const val END_PREFIX = "END:"

    fun parseDate(mmexDate: String): LocalDate =
        LocalDate.parse(MmexDateFormat.parseDatePart(mmexDate))

    fun formatOccurrence(date: LocalDate): String =
        MmexDateFormat.formatTransDate(date)

    fun encodeEndDate(endDate: LocalDate?): String? =
        endDate?.let { "$END_PREFIX${it}" }

    fun decodeEndDate(transactionNumber: String?): LocalDate? {
        if (transactionNumber.isNullOrBlank()) return null
        if (!transactionNumber.startsWith(END_PREFIX)) return null
        return runCatching { LocalDate.parse(transactionNumber.removePrefix(END_PREFIX)) }.getOrNull()
    }

    /**
     * Advance [from] by one period for the given MMEX repeats code.
     * [numOccurrences] is the interval X for EVERY_X_* / IN_X_* types.
     */
    fun advance(from: LocalDate, repeats: Int, numOccurrences: Int?): LocalDate? {
        val b = MmexRepeats.base(repeats)
        val n = (numOccurrences ?: 1).coerceAtLeast(1)
        return when (b) {
            MmexRepeats.ONCE -> null
            MmexRepeats.WEEKLY -> from.plus(7, DateTimeUnit.DAY)
            MmexRepeats.BIWEEKLY -> from.plus(14, DateTimeUnit.DAY)
            MmexRepeats.FOUR_WEEKS -> from.plus(28, DateTimeUnit.DAY)
            MmexRepeats.DAILY -> from.plus(1, DateTimeUnit.DAY)
            MmexRepeats.MONTHLY -> from.plus(1, DateTimeUnit.MONTH)
            MmexRepeats.BIMONTHLY -> from.plus(2, DateTimeUnit.MONTH)
            MmexRepeats.QUARTERLY -> from.plus(3, DateTimeUnit.MONTH)
            MmexRepeats.FOUR_MONTHS -> from.plus(4, DateTimeUnit.MONTH)
            MmexRepeats.SEMIANNUALLY -> from.plus(6, DateTimeUnit.MONTH)
            MmexRepeats.ANNUALLY -> from.plus(1, DateTimeUnit.YEAR)
            MmexRepeats.MONTHLY_LAST_DAY -> lastDayOfMonth(from.plus(1, DateTimeUnit.MONTH))
            MmexRepeats.MONTHLY_LAST_BUSINESS_DAY -> lastBusinessDayOfMonth(from.plus(1, DateTimeUnit.MONTH))
            MmexRepeats.IN_X_DAYS, MmexRepeats.EVERY_X_DAYS -> from.plus(n, DateTimeUnit.DAY)
            MmexRepeats.IN_X_MONTHS, MmexRepeats.EVERY_X_MONTHS -> from.plus(n, DateTimeUnit.MONTH)
            else -> from.plus(1, DateTimeUnit.MONTH)
        }
    }

    /**
     * Count occurrences from [start] through [end] inclusive for types where
     * NUMOCCURRENCES means remaining payments (not interval).
     */
    fun countRemaining(
        start: LocalDate,
        end: LocalDate,
        repeats: Int,
        intervalForEveryX: Int?
    ): Int {
        if (MmexRepeats.base(repeats) == MmexRepeats.ONCE) return 1
        if (end < start) return 0
        var count = 0
        var cursor = start
        while (cursor <= end && count < 10_000) {
            count++
            cursor = advance(cursor, repeats, intervalForEveryX) ?: break
        }
        return count
    }

    private fun lastDayOfMonth(anyDayInMonth: LocalDate): LocalDate {
        val firstNext = LocalDate(anyDayInMonth.year, anyDayInMonth.monthNumber, 1)
            .plus(1, DateTimeUnit.MONTH)
        return firstNext.plus(-1, DateTimeUnit.DAY)
    }

    private fun lastBusinessDayOfMonth(anyDayInMonth: LocalDate): LocalDate {
        var d = lastDayOfMonth(anyDayInMonth)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plus(-1, DateTimeUnit.DAY)
        }
        return d
    }
}

package com.konhit.financeapp.domain.model

/**
 * MMEX BILLSDEPOSITS_V1.REPEATS base codes (value % 100).
 * Auto mode bits live in value / 100 (ignored for app behaviour — we always prompt).
 */
object MmexRepeats {
    const val ONCE = 0
    const val WEEKLY = 1
    const val BIWEEKLY = 2
    const val MONTHLY = 3
    const val BIMONTHLY = 4
    const val QUARTERLY = 5
    const val SEMIANNUALLY = 6
    const val ANNUALLY = 7
    const val FOUR_MONTHS = 8
    const val FOUR_WEEKS = 9
    const val DAILY = 10
    const val IN_X_DAYS = 11
    const val IN_X_MONTHS = 12
    const val EVERY_X_DAYS = 13
    const val EVERY_X_MONTHS = 14
    const val MONTHLY_LAST_DAY = 15
    const val MONTHLY_LAST_BUSINESS_DAY = 16

    fun base(repeats: Int): Int = ((repeats % 100) + 100) % 100

    fun isEveryX(repeats: Int): Boolean {
        val b = base(repeats)
        return b == EVERY_X_DAYS || b == EVERY_X_MONTHS || b == IN_X_DAYS || b == IN_X_MONTHS
    }
}

enum class ScheduleMode {
    ONCE,
    DAILY,
    MONTHLY,
    DAY_OF_WEEK,
    CUSTOM
}

enum class CustomPeriod {
    WEEK,
    MONTH,
    YEAR
}

data class ScheduleRecurrence(
    val mode: ScheduleMode,
    /** For CUSTOM: every N periods. Ignored for fixed modes. */
    val everyN: Int = 1,
    val customPeriod: CustomPeriod = CustomPeriod.MONTH
) {
    fun toRepeatsAndOccurrences(
        remainingOrInterval: Int
    ): Pair<Int, Int> = when (mode) {
        ScheduleMode.ONCE -> MmexRepeats.ONCE to 1
        ScheduleMode.DAILY -> MmexRepeats.DAILY to remainingOrInterval
        ScheduleMode.MONTHLY -> MmexRepeats.MONTHLY to remainingOrInterval
        ScheduleMode.DAY_OF_WEEK -> MmexRepeats.WEEKLY to remainingOrInterval
        ScheduleMode.CUSTOM -> customToRepeats(remainingOrInterval)
    }

    private fun customToRepeats(remainingOrInterval: Int): Pair<Int, Int> {
        val n = everyN.coerceAtLeast(1)
        return when (customPeriod) {
            CustomPeriod.WEEK -> when (n) {
                1 -> MmexRepeats.WEEKLY to remainingOrInterval
                2 -> MmexRepeats.BIWEEKLY to remainingOrInterval
                4 -> MmexRepeats.FOUR_WEEKS to remainingOrInterval
                else -> MmexRepeats.EVERY_X_DAYS to (n * 7) // interval in NUMOCCURRENCES
            }
            CustomPeriod.MONTH -> when (n) {
                1 -> MmexRepeats.MONTHLY to remainingOrInterval
                2 -> MmexRepeats.BIMONTHLY to remainingOrInterval
                3 -> MmexRepeats.QUARTERLY to remainingOrInterval
                4 -> MmexRepeats.FOUR_MONTHS to remainingOrInterval
                6 -> MmexRepeats.SEMIANNUALLY to remainingOrInterval
                12 -> MmexRepeats.ANNUALLY to remainingOrInterval
                else -> MmexRepeats.EVERY_X_MONTHS to n
            }
            CustomPeriod.YEAR -> when (n) {
                1 -> MmexRepeats.ANNUALLY to remainingOrInterval
                else -> MmexRepeats.EVERY_X_MONTHS to (n * 12)
            }
        }
    }

    /** True when NUMOCCURRENCES stores the interval X rather than remaining payments. */
    fun usesOccurrencesAsInterval(): Boolean {
        if (mode != ScheduleMode.CUSTOM) return false
        val n = everyN.coerceAtLeast(1)
        return when (customPeriod) {
            CustomPeriod.WEEK -> n !in listOf(1, 2, 4)
            CustomPeriod.MONTH -> n !in listOf(1, 2, 3, 4, 6, 12)
            CustomPeriod.YEAR -> n != 1
        }
    }

    companion object {
        fun fromRepeats(repeats: Int, numOccurrences: Int?): ScheduleRecurrence {
            val b = MmexRepeats.base(repeats)
            val n = numOccurrences ?: -1
            return when (b) {
                MmexRepeats.ONCE -> ScheduleRecurrence(ScheduleMode.ONCE)
                MmexRepeats.DAILY -> ScheduleRecurrence(ScheduleMode.DAILY)
                MmexRepeats.MONTHLY -> ScheduleRecurrence(ScheduleMode.MONTHLY)
                MmexRepeats.WEEKLY -> ScheduleRecurrence(ScheduleMode.DAY_OF_WEEK)
                MmexRepeats.BIWEEKLY -> ScheduleRecurrence(ScheduleMode.CUSTOM, 2, CustomPeriod.WEEK)
                MmexRepeats.FOUR_WEEKS -> ScheduleRecurrence(ScheduleMode.CUSTOM, 4, CustomPeriod.WEEK)
                MmexRepeats.BIMONTHLY -> ScheduleRecurrence(ScheduleMode.CUSTOM, 2, CustomPeriod.MONTH)
                MmexRepeats.QUARTERLY -> ScheduleRecurrence(ScheduleMode.CUSTOM, 3, CustomPeriod.MONTH)
                MmexRepeats.FOUR_MONTHS -> ScheduleRecurrence(ScheduleMode.CUSTOM, 4, CustomPeriod.MONTH)
                MmexRepeats.SEMIANNUALLY -> ScheduleRecurrence(ScheduleMode.CUSTOM, 6, CustomPeriod.MONTH)
                MmexRepeats.ANNUALLY -> ScheduleRecurrence(ScheduleMode.CUSTOM, 1, CustomPeriod.YEAR)
                MmexRepeats.EVERY_X_DAYS -> {
                    val days = n.coerceAtLeast(1)
                    if (days % 7 == 0) ScheduleRecurrence(ScheduleMode.CUSTOM, days / 7, CustomPeriod.WEEK)
                    else ScheduleRecurrence(ScheduleMode.CUSTOM, days, CustomPeriod.WEEK) // best-effort
                }
                MmexRepeats.EVERY_X_MONTHS -> {
                    val months = n.coerceAtLeast(1)
                    if (months % 12 == 0) ScheduleRecurrence(ScheduleMode.CUSTOM, months / 12, CustomPeriod.YEAR)
                    else ScheduleRecurrence(ScheduleMode.CUSTOM, months, CustomPeriod.MONTH)
                }
                else -> ScheduleRecurrence(ScheduleMode.MONTHLY)
            }
        }
    }
}

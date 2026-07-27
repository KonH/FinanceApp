package com.konhit.financeapp.domain.model

data class ScheduledTransaction(
    val bdId: Long,
    val accountId: Long,
    val toAccountId: Long,
    val payeeId: Long,
    val type: TransactionType,
    val transAmount: Double,
    val toTransAmount: Double,
    val categId: Long?,
    val notes: String?,
    val nextOccurrenceDate: String,
    val repeats: Int,
    val numOccurrences: Int?,
    val status: String?,
    val transactionNumber: String?,
    val color: Int = -1,
    val followupId: Long = -1
) {
    val recurrence: ScheduleRecurrence
        get() = ScheduleRecurrence.fromRepeats(repeats, numOccurrences)

    val endDateEncoded: String?
        get() = ScheduleDateMath.decodeEndDate(transactionNumber)?.toString()
}

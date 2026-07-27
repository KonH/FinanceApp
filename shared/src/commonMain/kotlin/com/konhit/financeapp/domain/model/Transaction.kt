package com.konhit.financeapp.domain.model

data class Transaction(
    val transId: Long,
    val accountId: Long,
    val toAccountId: Long,
    val payeeId: Long,
    val type: TransactionType,
    val transAmount: Double,
    val toTransAmount: Double,
    val categId: Long?,
    val transDate: String,
    val lastUpdatedTime: String?,
    val notes: String?,
    /**
     * MMEX FOLLOWUPID. `-1` for normal rows; source `BDID` when created from a schedule.
     * Encodes the boolean fromSchedule marker without altering CHECKINGACCOUNT_V1 schema.
     */
    val followupId: Long = -1L
) {
    val fromSchedule: Boolean get() = followupId != -1L
}

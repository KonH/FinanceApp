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
    val notes: String?
)

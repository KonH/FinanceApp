package com.konhit.financeapp.domain.model

enum class TransactionType(val mmexCode: String) {
    DEPOSIT("Deposit"),
    WITHDRAWAL("Withdrawal"),
    TRANSFER("Transfer");

    companion object {
        fun fromMmex(code: String): TransactionType =
            entries.first { it.mmexCode == code }
    }
}

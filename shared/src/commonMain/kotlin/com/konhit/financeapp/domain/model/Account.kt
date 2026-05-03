package com.konhit.financeapp.domain.model

data class Account(
    val id: Long,
    val name: String,
    val type: String,
    val initialBal: Double,
    val currencyId: Long,
    val status: String,
    val balance: Double = 0.0
)

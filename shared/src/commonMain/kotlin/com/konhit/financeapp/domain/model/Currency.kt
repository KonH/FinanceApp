package com.konhit.financeapp.domain.model

data class Currency(
    val id: Long,
    val name: String,
    val pfxSymbol: String?,
    val sfxSymbol: String?,
    val decimalPoint: String?,
    val groupSeparator: String?,
    val scale: Long?,
    val currencySymbol: String?
)

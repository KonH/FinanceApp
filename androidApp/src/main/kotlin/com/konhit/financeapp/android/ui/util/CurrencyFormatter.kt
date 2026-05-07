package com.konhit.financeapp.android.ui.util

import com.konhit.financeapp.domain.model.Currency

fun formatAmount(amount: Double, currency: Currency?): String {
    val decSep = currency?.decimalPoint?.takeIf { it.isNotEmpty() } ?: "."
    val grpSep = currency?.groupSeparator?.takeIf { it.isNotEmpty() } ?: " "
    val symbol = currency?.currencySymbol ?: currency?.pfxSymbol ?: ""

    val formatted = buildString {
        val absAmount = kotlin.math.abs(amount)
        val intPart = absAmount.toLong()
        val fracPart = Math.round((absAmount - intPart) * 100)

        val intStr = intPart.toString()
        val withSeparators = intStr.reversed()
            .chunked(3)
            .joinToString(grpSep)
            .reversed()

        if (amount < 0) append('-')
        append(withSeparators)
        append(decSep)
        append(fracPart.toString().padStart(2, '0'))
    }

    return if (symbol.isNotEmpty()) "$symbol $formatted" else formatted
}

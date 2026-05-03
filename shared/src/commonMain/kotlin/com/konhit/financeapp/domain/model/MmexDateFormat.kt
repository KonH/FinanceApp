package com.konhit.financeapp.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

object MmexDateFormat {

    fun formatTransDate(date: LocalDate): String =
        "${date.year}-${date.monthNumber.pad()}-${date.dayOfMonth.pad()}T00:00:00"

    fun formatLastUpdated(dateTime: LocalDateTime): String =
        "${dateTime.year}-${dateTime.monthNumber.pad()}-${dateTime.dayOfMonth.pad()}" +
        "T${dateTime.hour.pad()}:${dateTime.minute.pad()}:${dateTime.second.pad()}"

    fun parseDatePart(mmexDate: String): String =
        mmexDate.substringBefore('T')

    private fun Int.pad() = toString().padStart(2, '0')
}

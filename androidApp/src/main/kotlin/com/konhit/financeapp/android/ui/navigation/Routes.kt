package com.konhit.financeapp.android.ui.navigation

object Routes {
    const val FIRST_LAUNCH  = "first_launch"
    const val MAIN          = "main"
    const val SETTINGS      = "settings"
    const val ACCOUNTS      = "accounts"
    const val CATEGORIES    = "categories"
    const val CURRENCIES    = "currencies"
    const val SCHEDULED     = "scheduled"
    const val SCHEDULED_EDIT = "scheduled_edit?bdId={bdId}"
    const val ACCOUNT       = "account/{accountId}"
    const val TRANSACTION   = "transaction?accountId={accountId}&transId={transId}"
    const val FILTER        = "filter/{accountId}"

    fun account(accountId: Long) = "account/$accountId"
    fun transactionAdd(accountId: Long) = "transaction?accountId=$accountId&transId=-1"
    fun transactionEdit(transId: Long) = "transaction?accountId=-1&transId=$transId"
    fun filter(accountId: Long = -1L) = "filter/$accountId"
    fun scheduledEdit(bdId: Long = -1L) = "scheduled_edit?bdId=$bdId"
}

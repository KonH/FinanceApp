package com.konhit.financeapp.android.di

import com.konhit.financeapp.android.ui.screens.account.AccountViewModel
import com.konhit.financeapp.android.ui.screens.firstlaunch.FirstLaunchViewModel
import com.konhit.financeapp.android.ui.screens.main.MainViewModel
import com.konhit.financeapp.android.ui.screens.settings.SettingsViewModel
import com.konhit.financeapp.android.ui.screens.transaction.TransactionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    viewModel {
        FirstLaunchViewModel(
            authManager = get(),
            initialise  = get(),
            openFile    = get(),
            settings    = get(),
            cacheDir    = androidContext().cacheDir
        )
    }

    viewModel {
        MainViewModel(
            accountRepo     = get(),
            transactionRepo = get(),
            settings        = get(),
            syncCoordinator = get()
        )
    }

    viewModel { (accountId: Long) ->
        AccountViewModel(
            accountRepo     = get(),
            transactionRepo = get(),
            settings        = get(),
            syncCoordinator = get(),
            savedStateHandle = get()
        )
    }

    viewModel { (initialAccountId: Long?, editTransId: Long?) ->
        TransactionViewModel(
            transactionRepo  = get(),
            accountRepo      = get(),
            categoryRepo     = get(),
            syncCoordinator  = get(),
            dbHolder         = get(),
            initialAccountId = initialAccountId,
            editTransId      = editTransId
        )
    }

    viewModel {
        SettingsViewModel(
            settings        = get(),
            accountRepo     = get(),
            categoryRepo    = get(),
            currencyRepo    = get(),
            syncCoordinator = get()
        )
    }
}

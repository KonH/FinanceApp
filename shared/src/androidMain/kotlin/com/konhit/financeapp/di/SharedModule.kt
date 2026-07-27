package com.konhit.financeapp.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.konhit.financeapp.data.repository.AccountRepositoryImpl
import com.konhit.financeapp.data.repository.CategoryRepositoryImpl
import com.konhit.financeapp.data.repository.CurrencyRepositoryImpl
import com.konhit.financeapp.data.repository.ScheduledTransactionRepositoryImpl
import com.konhit.financeapp.data.repository.SettingsRepositoryImpl
import com.konhit.financeapp.data.repository.TransactionRepositoryImpl
import com.konhit.financeapp.db.DatabaseFactory
import com.konhit.financeapp.db.DatabaseHolder
import com.konhit.financeapp.domain.repository.AccountRepository
import com.konhit.financeapp.domain.repository.CategoryRepository
import com.konhit.financeapp.domain.repository.CurrencyRepository
import com.konhit.financeapp.domain.repository.ScheduledTransactionRepository
import com.konhit.financeapp.domain.repository.SettingsRepository
import com.konhit.financeapp.domain.repository.TransactionRepository
import com.konhit.financeapp.domain.usecase.ComputeBalanceUseCase
import com.konhit.financeapp.domain.usecase.ComputeBudgetUsageUseCase
import com.konhit.financeapp.domain.usecase.InitialiseFileUseCase
import com.konhit.financeapp.domain.usecase.OpenFileUseCase
import com.konhit.financeapp.domain.usecase.ProcessDueScheduledUseCase
import com.konhit.financeapp.feature.FeatureFlags
import com.konhit.financeapp.drive.DriveAuthManager
import com.konhit.financeapp.drive.DriveConflictDetector
import com.konhit.financeapp.drive.DriveFileClient
import com.konhit.financeapp.drive.SyncCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sharedModule = module {

    single { DatabaseHolder() }
    single { DatabaseFactory(androidContext()) }

    single {
        PreferenceDataStoreFactory.create {
            androidContext().preferencesDataStoreFile("app_settings")
        }
    }

    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    single { DriveAuthManager(androidContext()) }
    single { DriveFileClient(get()) }
    single { DriveConflictDetector() }
    single {
        SyncCoordinator(
            driveClient      = get(),
            conflictDetector = get(),
            dbFactory        = get(),
            dbHolder         = get(),
            settings         = get(),
            authManager      = get(),
            cacheDir         = androidContext().cacheDir
        )
    }

    single<AccountRepository>     { AccountRepositoryImpl(get(), get()) }
    single<TransactionRepository>  { TransactionRepositoryImpl(get(), get()) }
    single<CategoryRepository>     { CategoryRepositoryImpl(get()) }
    single<CurrencyRepository>     { CurrencyRepositoryImpl(get()) }
    single<ScheduledTransactionRepository> { ScheduledTransactionRepositoryImpl(get(), get()) }

    single { FeatureFlags() }

    factory { ComputeBalanceUseCase() }
    factory { ComputeBudgetUsageUseCase() }
    factory { InitialiseFileUseCase(get(), get(), get(), get(), get()) }
    factory { OpenFileUseCase(get(), get(), get()) }
    factory { ProcessDueScheduledUseCase(get(), get(), get(), get()) }
}

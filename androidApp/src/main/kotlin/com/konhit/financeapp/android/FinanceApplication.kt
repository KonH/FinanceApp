package com.konhit.financeapp.android

import android.app.Application
import com.konhit.financeapp.android.di.appModule
import com.konhit.financeapp.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FinanceApplication)
            modules(sharedModule, appModule)
        }
    }
}

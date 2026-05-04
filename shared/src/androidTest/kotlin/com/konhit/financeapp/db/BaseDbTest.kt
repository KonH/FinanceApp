package com.konhit.financeapp.db

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import org.junit.After
import org.junit.Before
import java.io.File

abstract class BaseDbTest {

    protected lateinit var dbFile: File
    protected lateinit var database: MmexDatabase
    protected var defaultPayeeId: Long = -1L

    private lateinit var driver: SqlDriver

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appCtx: Context = instrumentation.targetContext
        val testCtx: Context = instrumentation.context

        dbFile = File(appCtx.cacheDir, "test_${System.currentTimeMillis()}.mmb")
        testCtx.assets.open("example.mmb").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }

        val conn = DatabaseFactory(appCtx).openExisting(dbFile)
        database = conn.database
        driver = conn.driver
        defaultPayeeId = database.payeeQueries.selectFirst().executeAsOneOrNull() ?: -1L
    }

    @After
    fun teardown() {
        driver.close()
        dbFile.delete()
    }
}

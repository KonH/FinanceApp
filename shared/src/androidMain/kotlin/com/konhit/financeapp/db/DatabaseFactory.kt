package com.konhit.financeapp.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

class DatabaseFactory(private val context: Context) {

    /**
     * Opens an EXISTING .mmb file.
     *
     * Overrides onCreate() as a no-op so Schema.create() never runs.
     * This is safe because MMEX files already have all required tables,
     * and SQLiteOpenHelper only calls onCreate() when user_version == 0.
     * Overriding it prevents any DDL from touching the existing schema.
     *
     * user_version will be bumped to schema.version by SQLiteOpenHelper after this,
     * which is harmless — MMEX uses INFOTABLE_V1.DATAVERSION for its own versioning.
     */
    fun openExisting(file: File): DatabaseConnection {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        val driver = AndroidSqliteDriver(
            schema = MmexDatabase.Schema,
            context = context,
            name = file.absolutePath,
            callback = object : AndroidSqliteDriver.Callback(MmexDatabase.Schema) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Never run DDL on existing files.
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Never migrate MMEX files.
                }
            }
        )
        return DatabaseConnection(MmexDatabase(driver), driver)
    }

    /**
     * Creates a brand-new .mmb file and initialises the full MMEX schema.
     * Schema.create() is called exactly once here, and never on existing files.
     */
    fun createNew(file: File): DatabaseConnection {
        require(!file.exists()) { "File already exists: ${file.absolutePath}" }
        file.parentFile?.mkdirs()
        val driver = AndroidSqliteDriver(
            schema = MmexDatabase.Schema,
            context = context,
            name = file.absolutePath,
            callback = object : AndroidSqliteDriver.Callback(MmexDatabase.Schema) {
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // No migration path — new files start at current schema version.
                }
            }
        )
        return DatabaseConnection(MmexDatabase(driver), driver)
    }
}

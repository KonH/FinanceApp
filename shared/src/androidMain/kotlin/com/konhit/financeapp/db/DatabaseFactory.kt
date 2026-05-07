package com.konhit.financeapp.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

class DatabaseFactory(private val context: Context) {

    /**
     * Opens an EXISTING .mmb file.
     *
     * MMEX sets user_version=20; SQLDelight's schema defaults to version 1.
     * sqlite-framework 2.4+ no longer delegates onDowngrade through the Callback,
     * so we normalise user_version via the raw Android SQLiteDatabase API before
     * AndroidSqliteDriver opens the file, eliminating any version mismatch.
     * This is safe because MMEX uses INFOTABLE_V1.DATAVERSION for its own versioning.
     */
    fun openExisting(file: File): DatabaseConnection {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        val schemaVersion = MmexDatabase.Schema.version.toInt()
        AndroidSQLiteDatabase.openDatabase(file.absolutePath, null, AndroidSQLiteDatabase.OPEN_READWRITE).use { db ->
            if (db.version != schemaVersion) db.version = schemaVersion
        }
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
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Safety net for any future version mismatch.
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

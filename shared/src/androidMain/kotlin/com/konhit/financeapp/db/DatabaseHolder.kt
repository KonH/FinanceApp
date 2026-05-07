package com.konhit.financeapp.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import java.io.File

class DatabaseHolder {

    var database: MmexDatabase? = null
        private set

    var currentFile: File? = null
        private set

    var defaultPayeeId: Long = -1L
        private set

    private var driver: SqlDriver? = null

    fun open(db: MmexDatabase, driver: SqlDriver, file: File, payeeId: Long) {
        this.driver?.close()
        this.driver = driver
        database = db
        currentFile = file
        defaultPayeeId = payeeId
    }

    fun checkpoint() {
        // execute() routes through compileStatement/executeUpdateDelete and throws when the SQL
        // returns rows. wal_checkpoint returns 3 columns, so we use executeQuery (rawQuery path).
        driver?.executeQuery(null, "PRAGMA wal_checkpoint(TRUNCATE)", { QueryResult.Value(Unit) }, 0, null)
    }

    fun close() {
        driver?.close()
        driver = null
        database = null
        currentFile = null
        defaultPayeeId = -1L
    }

    fun requireDb(): MmexDatabase =
        database ?: error("No database open")
}

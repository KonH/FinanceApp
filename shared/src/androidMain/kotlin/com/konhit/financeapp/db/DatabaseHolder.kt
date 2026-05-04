package com.konhit.financeapp.db

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

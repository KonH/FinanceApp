package com.konhit.financeapp.db

import java.io.File

class DatabaseHolder {

    var database: MmexDatabase? = null
        private set

    var currentFile: File? = null
        private set

    var defaultPayeeId: Long = -1L
        private set

    fun open(db: MmexDatabase, file: File, payeeId: Long) {
        database?.close()
        database = db
        currentFile = file
        defaultPayeeId = payeeId
    }

    fun close() {
        database?.close()
        database = null
        currentFile = null
        defaultPayeeId = -1L
    }

    fun requireDb(): MmexDatabase =
        database ?: error("No database open")
}

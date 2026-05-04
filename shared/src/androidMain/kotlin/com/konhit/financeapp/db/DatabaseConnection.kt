package com.konhit.financeapp.db

import app.cash.sqldelight.db.SqlDriver

data class DatabaseConnection(
    val database: MmexDatabase,
    val driver: SqlDriver
)

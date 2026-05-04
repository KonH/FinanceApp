package com.konhit.financeapp.db

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TC10_SchemaPreservationTest : BaseDbTest() {

    @Test
    fun schemaPreservation_afterWrite_tablesTriggersViews() {
        database.transactionQueries.insert(
            transId         = System.currentTimeMillis() * 1000L,
            accountId       = 1748881720210000L,
            toAccountId     = -1L,
            payeeId         = defaultPayeeId,
            transCode       = "Withdrawal",
            transAmount     = 1.0,
            toTransAmount   = 0.0,
            notes           = "schema check",
            categId         = 10L,
            transDate       = "2026-05-04T00:00:00",
            lastUpdatedTime = "2026-05-04T12:00:00"
        )

        // Open a second read-only connection to inspect sqlite_master without disturbing the
        // primary connection that BaseDbTest.teardown() expects to close.
        val sqlDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            val tableCount = DatabaseUtils.longForQuery(
                sqlDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null
            )
            val triggerCount = DatabaseUtils.longForQuery(
                sqlDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'",
                null
            )
            val viewCount = DatabaseUtils.longForQuery(
                sqlDb,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='view'",
                null
            )

            assertEquals(23L, tableCount)
            assertEquals(0L, triggerCount)
            assertEquals(0L, viewCount)
        } finally {
            sqlDb.close()
        }
    }
}

package com.konhit.financeapp.db

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that DatabaseHolder.checkpoint() flushes WAL data into the main SQLite file
 * before it is copied (uploaded). Without the checkpoint, a file copy taken while the
 * database is open in WAL mode may miss recent writes, causing data loss on sync.
 */
@RunWith(AndroidJUnit4::class)
class TC11_WalCheckpointTest {

    private lateinit var appCtx: Context
    private lateinit var dbFile: File
    private lateinit var copyFile: File

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        appCtx = instrumentation.targetContext
        val testCtx = instrumentation.context

        dbFile = File(appCtx.cacheDir, "tc11_source_${System.currentTimeMillis()}.mmb")
        copyFile = File(appCtx.cacheDir, "tc11_copy_${System.currentTimeMillis()}.mmb")

        testCtx.assets.open("example.mmb").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    @After
    fun teardown() {
        dbFile.delete()
        copyFile.delete()
    }

    @Test
    fun checkpoint_flushesWalWriteIntoMainFile() {
        val factory = DatabaseFactory(appCtx)
        val conn = factory.openExisting(dbFile)
        val holder = DatabaseHolder()
        val payeeId = conn.database.payeeQueries.selectFirst().executeAsOneOrNull() ?: -1L
        holder.open(conn.database, conn.driver, dbFile, payeeId)

        // Pick a subcategory (PARENTID != -1) and reparent it to root.
        val sub = conn.database.categoryQueries.selectAll().executeAsList()
            .first { it.PARENTID != -1L }
        assertNotEquals(-1L, sub.PARENTID)

        conn.database.categoryQueries.update(sub.CATEGNAME, -1L, sub.CATEGID)

        // Flush WAL → main file before copying (this is what the fix does before upload).
        holder.checkpoint()

        dbFile.copyTo(copyFile, overwrite = true)
        conn.driver.close()

        // Open the copy — the reparented category must be visible with the new PARENTID.
        val copyConn = factory.openExisting(copyFile)
        val found = copyConn.database.categoryQueries.selectAll().executeAsList()
            .first { it.CATEGID == sub.CATEGID }
        copyConn.driver.close()

        assertEquals(
            "PARENTID must be -1 in the copied file after checkpoint",
            -1L,
            found.PARENTID
        )
    }

    @Test
    fun withoutCheckpoint_copyMayMissWalWrite() {
        val factory = DatabaseFactory(appCtx)
        val conn = factory.openExisting(dbFile)
        val holder = DatabaseHolder()
        val payeeId = conn.database.payeeQueries.selectFirst().executeAsOneOrNull() ?: -1L
        holder.open(conn.database, conn.driver, dbFile, payeeId)

        val sub = conn.database.categoryQueries.selectAll().executeAsList()
            .first { it.PARENTID != -1L }
        val originalParentId = sub.PARENTID

        conn.database.categoryQueries.update(sub.CATEGNAME, -1L, sub.CATEGID)

        // No checkpoint — copy the main file as-is (simulates old uploadCurrent behaviour).
        dbFile.copyTo(copyFile, overwrite = true)
        conn.driver.close()

        val copyConn = factory.openExisting(copyFile)
        val found = copyConn.database.categoryQueries.selectAll().executeAsList()
            .first { it.CATEGID == sub.CATEGID }
        copyConn.driver.close()

        // The copy still has the original PARENTID because the write was only in the WAL.
        assertEquals(
            "Without checkpoint the copy should reflect the pre-write PARENTID",
            originalParentId,
            found.PARENTID
        )
    }
}

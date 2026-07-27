package com.konhit.financeapp.drive

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DriveConflictDetectorTest {

    private val detector = DriveConflictDetector()
    private val t0 = Instant.fromEpochMilliseconds(1_000_000)
    private val t1 = Instant.fromEpochMilliseconds(2_000_000)

    @Test
    fun noLastSync_neverConflicts() {
        assertIs<ConflictResult.NoConflict>(detector.check(t1, null, pendingUpload = false))
        assertIs<ConflictResult.KeepLocalAndUpload>(detector.check(t1, null, pendingUpload = true))
    }

    @Test
    fun remoteUnchanged_noPending_noConflict() {
        assertIs<ConflictResult.NoConflict>(detector.check(t0, t0, pendingUpload = false))
        assertIs<ConflictResult.NoConflict>(detector.check(t0, t1, pendingUpload = false))
    }

    @Test
    fun remoteUnchanged_withPending_keepLocalAndUpload_notConflictDialog() {
        // Regression: failed onStop upload used to force Keep local / Use remote
        // even though Drive still has the same modifiedTime as last sync.
        val result = detector.check(t0, t0, pendingUpload = true)
        assertIs<ConflictResult.KeepLocalAndUpload>(result)
    }

    @Test
    fun remoteNewer_noPending_autoTakeRemote() {
        assertIs<ConflictResult.NoConflict>(detector.check(t1, t0, pendingUpload = false))
    }

    @Test
    fun remoteNewer_withPending_isConflict() {
        val result = detector.check(t1, t0, pendingUpload = true)
        assertIs<ConflictResult.Conflict>(result)
        assertEquals(t1, result.remoteTime)
        assertEquals(t0, result.localSyncTime)
    }
}

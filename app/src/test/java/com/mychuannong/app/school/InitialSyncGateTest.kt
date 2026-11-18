package com.mychuannong.app.school

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSyncGateTest {
    @Test fun manualRefreshCancelsOnlyTheInFlightInitialSync() {
        val gate = InitialSyncGate()
        val initial = Job()
        val duplicate = Job()
        assertTrue(gate.register(initial))
        assertFalse(gate.register(duplicate))
        gate.cancelForUserAction()
        assertTrue(initial.isCancelled)
        assertFalse(duplicate.isCancelled)
        assertTrue(gate.register(duplicate))
        gate.finish(initial) // A late completion cannot clear a newer job.
        val third = Job()
        assertFalse(gate.register(third))
        gate.finish(duplicate)
        assertTrue(gate.register(third))
    }
}

package org.mobilism.material.remote

import org.junit.Assert.*
import org.junit.Test

class NoticeDismissalGateTest {
    @Test fun dismissalWaitsForThreeSecondsAfterRenderingAndNeverUnlocksRequiredUpdates() {
        var time = 0L
        val gate = NoticeDismissalGate { time }
        time = 10000
        assertFalse(gate.canDismiss(false))
        gate.rendered()
        time += 2999
        assertEquals(1, gate.remainingSeconds)
        assertFalse(gate.canDismiss(false))
        time++
        assertTrue(gate.canDismiss(false))
        assertFalse(gate.canDismiss(true))
        gate.rendered()
        assertTrue(gate.canDismiss(false))
    }

    @Test fun backgroundTimeCannotFinishAnIncompleteHold() {
        var time = 0L
        val gate = NoticeDismissalGate { time }
        gate.rendered(); time = 2000; gate.pause()
        time = 60000; assertFalse(gate.canDismiss(false))
        gate.rendered(); time += 2999; assertFalse(gate.canDismiss(false))
        time++; assertTrue(gate.canDismiss(false))
        gate.pause(); assertTrue(gate.canDismiss(false))
    }
}

package app.luoxianlv

import app.luoxianlv.service.DisplayStability
import org.junit.Assert.*
import org.junit.Test

class DisplayStabilityTest {
    @Test fun waitsForStableSizeAndRotationIncludingHalfTurn() {
        val gate = DisplayStability()
        val portrait = Triple(1080, 2400, 0)
        val landscape = Triple(2400, 1080, 1)
        assertFalse(gate.ready(portrait, 0))
        assertFalse(gate.ready(landscape, 100))
        assertFalse(gate.ready(landscape, 339))
        assertTrue(gate.ready(landscape, 340))
        assertFalse(gate.ready(landscape.copy(third = 3), 350))
        assertTrue(gate.ready(landscape.copy(third = 3), 590))
    }
    @Test fun invalidDisplayAndManualResetRequireFreshStability() {
        val gate = DisplayStability()
        val display = Triple(2400, 1080, 1)
        gate.ready(display, 0)
        assertFalse(gate.ready(Triple(0, 0, 1), 300))
        assertFalse(gate.ready(display, 301))
        assertTrue(gate.ready(display, 541))
        gate.reset()
        assertFalse(gate.ready(display, 1000))
    }
}

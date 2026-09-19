package app.luoxianlv

import app.luoxianlv.service.PlaybackCoordinates
import app.luoxianlv.core.score.PlayMode
import app.luoxianlv.data.KeyLayout
import org.junit.Assert.*
import org.junit.Test

class PlaybackCoordinatesTest {
    private fun layout(xs: FloatArray) = KeyLayout(xs, .6f,
        PlayMode.values().associateWith { floatArrayOf(.5f, .4f) })

    @Test fun rejectsCollapsedOrInvalidCoordinates() {
        assertTrue(PlaybackCoordinates.validLayout(layout(FloatArray(8) { .18f + it * .09f })))
        assertFalse(PlaybackCoordinates.validLayout(layout(FloatArray(8) { .01f })))
        assertFalse(PlaybackCoordinates.validPoint(Float.NaN, .5f))
        assertFalse(PlaybackCoordinates.validPoint(-.5f, 2f))
        assertFalse(PlaybackCoordinates.validPoint(0f, 1f))
        assertFalse(PlaybackCoordinates.validLayout(layout(floatArrayOf(.2f))))
    }
}

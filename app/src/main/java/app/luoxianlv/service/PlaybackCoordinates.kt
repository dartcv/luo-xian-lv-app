package app.luoxianlv.service

import app.luoxianlv.data.KeyLayout
import app.luoxianlv.core.score.PlayMode

internal object PlaybackCoordinates {
    fun validPoint(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x > 0f && x < 1f && y > 0f && y < 1f

    fun validLayout(layout: KeyLayout): Boolean =
        layout.noteX.size == 8 &&
            layout.noteX.all { validPoint(it, layout.noteY) } &&
            layout.noteX.asList().zipWithNext().all { (a, b) -> b > a } &&
            PlayMode.values().all { mode ->
                val point = layout.modes[mode]
                point != null && point.size == 2 && validPoint(point[0], point[1])
            }
}

package com.example.autoplaymusic.core.score

import kotlin.math.roundToLong

data class TimedMidiNote(
    val pitch: Int,
    val startUs: Long,
    val endUs: Long,
    val keyEndUs: Long,
    val velocity: Int,
    val track: Int,
    val channel: Int,
)

/** Integrate the tempo map once; do not round each note to a millisecond or beat grid. */
class MidiTiming(
    document: MidiParser.Document,
) {
    private val resolution = document.resolution.also { require(it > 0) }
    private val changes =
        (listOf(MidiParser.Tempo(0, 500000)) + document.tempos)
            .associateBy { it.tick }
            .values
            .sortedBy { it.tick }
    private val ticks = changes.map { it.tick }.toLongArray()
    private val elapsed = DoubleArray(changes.size)

    init {
        changes.forEachIndexed { i, change ->
            require(change.tick >= 0 && change.micros > 0)
            if (i > 0) {
                elapsed[i] = elapsed[i - 1] +
                    (change.tick - changes[i - 1].tick) * changes[i - 1].micros.toDouble() / resolution
            }
        }
    }

    fun microsAt(tick: Long): Long {
        require(tick >= 0)
        val found = ticks.binarySearch(tick)
        val i = if (found >= 0) found else -found - 2
        return (elapsed[i] + (tick - ticks[i]) * changes[i].micros.toDouble() / resolution).roundToLong()
    }

    fun note(
        note: MidiParser.Note,
        track: Int,
    ) = TimedMidiNote(
        note.pitch,
        microsAt(note.start),
        microsAt(note.end),
        microsAt(note.keyEnd),
        note.velocity,
        track,
        note.channel,
    )
}

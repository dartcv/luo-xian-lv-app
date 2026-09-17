package com.example.autoplaymusic.core.score

enum class MidiVoice(
    val label: String,
) {
    MELODY("旋律优先"),
    ONSETS("逐起音"),
}

data class VoicedMidi(
    val notes: List<TimedMidiNote>,
    val groupedOnsets: Int,
)

/** Explicit single-voice arrangement. The complete source stays in CompiledScore.sourceNotes. */
object MidiVoicing {
    fun arrange(
        source: List<TimedMidiNote>,
        voice: MidiVoice,
    ): VoicedMidi {
        val groups = mutableListOf<MutableList<TimedMidiNote>>()
        source.forEach { note ->
            val last = groups.lastOrNull()
            val simultaneous = last != null && note.startUs == last.first().startUs
            // Anchor to the first onset (no transitive clustering). Never merge
            // repeated strikes or non-overlapping fast notes into a chord.
            val humanizedChord =
                voice == MidiVoice.MELODY && last != null &&
                    note.startUs - last.first().startUs <= 12000 &&
                    last.all { it.keyEndUs > note.startUs && it.pitch != note.pitch }
            if (simultaneous || humanizedChord) last!!.add(note) else groups += mutableListOf(note)
        }
        val selected = mutableListOf<TimedMidiNote>()
        groups.forEach { group ->
            val next = group.maxWith(compareBy<TimedMidiNote> { it.pitch }.thenBy { it.velocity })
            val previous = selected.lastOrNull()
            val legatoTolerance = previous?.let { minOf(25000L, (it.keyEndUs - it.startUs) / 10) } ?: 0
            // A lower accompaniment onset must not replace a still-held upper
            // melody. Pedal tails do not block descending melody entries.
            if (voice == MidiVoice.MELODY && previous != null && next.pitch < previous.pitch &&
                previous.keyEndUs - next.startUs > legatoTolerance
            ) {
                return@forEach
            }
            selected += next
        }
        return VoicedMidi(selected, source.map { it.startUs }.distinct().size - groups.size)
    }
}

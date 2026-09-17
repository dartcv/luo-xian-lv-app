package com.example.autoplaymusic.core.score

data class CompiledNote(
    val startUs: Long,
    val endUs: Long,
    val originalPitch: Int,
    val mappedPitch: Int,
    val velocity: Int,
    val primaryKey: Int,
    val mode: PlayMode,
    val halfTone: Boolean,
) {
    val startMs get() = startUs / 1000.0
    val durationMs get() = (endUs - startUs) / 1000.0
}

data class CompiledScore(
    val profileId: String,
    val bpm: Int,
    val durationUs: Long,
    val notes: List<CompiledNote>,
    val sourceNotes: List<TimedMidiNote>,
    val octaveShift: Int,
    val foldedNotes: Int,
    val groupedOnsets: Int,
    val warnings: List<String> = emptyList(),
    val transitionTailMs: Long = 0L,
) {
    val sourceNoteCount get() = sourceNotes.size
    val durationMs get() = durationUs / 1000.0
    val reducedNotes get() = sourceNoteCount - notes.size

    /** Absolute endpoints become durations only at this legacy serialization boundary. */
    fun toLegacyEvents(): List<NoteEvent> =
        buildList {
            var cursor = 0L
            notes.forEachIndexed { index, note ->
                require(note.endUs > note.startUs) { "编配时间线有无效时值" }
                val effectiveStart = maxOf(note.startUs, cursor)
                if (effectiveStart > cursor) add(NoteEvent.rest((effectiveStart - cursor) * bpm / 60000000.0))
                val next = notes.getOrNull(index + 1)
                val changingState =
                    next != null &&
                        (
                            next.mode != note.mode || next.halfTone != note.halfTone ||
                                kotlin.math.abs(next.mappedPitch - note.mappedPitch) >= 2
                        )
                // Smooth only inside an existing MIDI gap. Never move the next
                // source onset, otherwise many transitions accumulate drift.
                val gapUs = (next?.startUs ?: note.endUs) - note.endUs
                val tailUs = if (changingState) minOf(transitionTailMs * 1000, gapUs.coerceAtLeast(0)) else 0L
                add(
                    NoteEvent(
                        note.primaryKey,
                        note.mode,
                        ((note.endUs - note.startUs) + tailUs) * bpm / 60000000.0,
                        halfTone = note.halfTone,
                    ),
                )
                cursor = effectiveStart + (note.endUs - note.startUs) + tailUs
            }
            if (durationUs > cursor) add(NoteEvent.rest((durationUs - cursor) * bpm / 60000000.0))
        }
}

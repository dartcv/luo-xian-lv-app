package com.example.autoplaymusic.core.harmonica
import com.example.autoplaymusic.core.score.CompiledNote
import com.example.autoplaymusic.core.score.CompiledScore
import com.example.autoplaymusic.core.score.MidiParser
import com.example.autoplaymusic.core.score.MidiTiming
import com.example.autoplaymusic.core.score.MidiVoice
import com.example.autoplaymusic.core.score.MidiVoicing
import com.example.autoplaymusic.core.score.TimedMidiNote
import com.example.autoplaymusic.profile.GameProfile
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** MIDI timing, voice arrangement and pitch mapping are separate and independently testable. */
object HarmonicaCompiler {
    const val VERSION = 3

    fun compile(
        document: MidiParser.Document,
        trackIndexes: List<Int>,
        profile: GameProfile,
        voice: MidiVoice = MidiVoice.MELODY,
        smoothTransitions: Boolean = true,
    ): CompiledScore {
        require(trackIndexes.isNotEmpty()) { "至少选择一个音轨" }
        require(trackIndexes.all { it in document.tracks.indices }) { "音轨编号无效" }
        val timing = MidiTiming(document)
        val source =
            trackIndexes
                .distinct()
                .flatMap { index ->
                    document.tracks[index]
                        .notes
                        .filter { it.channel != 9 }
                        .map { timing.note(it, index) }
                }.filter { it.endUs > it.startUs }
                .sortedWith(compareBy<TimedMidiNote> { it.startUs }.thenByDescending { it.pitch })
        require(source.isNotEmpty()) { "选择的音轨没有旋律音符" }
        val voiced = MidiVoicing.arrange(source, voice)
        val mapped = HarmonicaPitchMapper.map(voiced.notes, profile)
        val bpm =
            (60000000.0 / (document.tempos.lastOrNull { it.tick == 0L }?.micros ?: 500000))
                .roundToInt()
                .coerceIn(20, 400)
        val compiled =
            voiced.notes.mapIndexed { i, note ->
                val key = mapped.keys[i]
                val end = minOf(note.endUs, voiced.notes.getOrNull(i + 1)?.startUs ?: note.endUs)
                val cappedEnd = minOf(end, note.startUs + profile.maxHoldMs * 1000L)
                val heldEnd =
                    note.startUs +
                        ((cappedEnd - note.startUs) * profile.sustainRatio.toDouble())
                            .roundToLong()
                            .coerceIn(1L, (end - note.startUs).coerceAtLeast(1L))
                CompiledNote(
                    note.startUs,
                    heldEnd,
                    note.pitch,
                    key.pitch,
                    note.velocity,
                    key.index,
                    key.mode,
                    key.halfTone,
                )
            }
        return CompiledScore(
            profile.id,
            bpm,
            maxOf(timing.microsAt(document.endTick), source.maxOf { it.endUs }),
            compiled,
            source,
            mapped.octaveShift,
            mapped.foldedNotes,
            voiced.groupedOnsets,
            document.warnings,
            if (smoothTransitions) profile.transitionTailMs else 0L,
        )
    }
}

package com.example.autoplaymusic.core.harmonica
import com.example.autoplaymusic.core.score.PlayMode
import com.example.autoplaymusic.core.score.TimedMidiNote
import com.example.autoplaymusic.profile.GameProfile
import kotlin.math.abs

data class HarmonicaKey(
    val pitch: Int,
    val index: Int,
    val mode: PlayMode,
    val halfTone: Boolean,
)

data class MappedPitches(
    val keys: List<HarmonicaKey>,
    val octaveShift: Int,
    val foldedNotes: Int,
)

object HarmonicaPitchMapper {
    fun keys(profile: GameProfile): List<HarmonicaKey> {
        require(profile.keyCount in 1..8) { "口风琴编译器支持 1～8 键" }
        val intervals = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        return listOf(PlayMode.LOWER to 48, PlayMode.NATURAL to 60, PlayMode.RAISE to 72)
            .flatMap { (mode, base) ->
                (0 until profile.keyCount).flatMap { key ->
                    (0..if (profile.hasSemitone) 1 else 0).map { half ->
                        HarmonicaKey(base + intervals[key] + half, key, mode, half == 1)
                    }
                }
            }.filter { it.pitch in profile.minMidi..profile.maxMidi }
            // Prefer the normal register's C over low-register i, etc.
            .sortedBy { if (it.pitch in 60..71 && it.mode == PlayMode.NATURAL || it.pitch >= 72 && it.mode == PlayMode.RAISE) 0 else 1 }
            .distinctBy { it.pitch }
    }

    fun map(
        notes: List<TimedMidiNote>,
        profile: GameProfile,
    ): MappedPitches {
        val available = keys(profile)
        require(available.isNotEmpty()) { "乐器没有可用音高" }
        val candidates =
            notes.map { note ->
                available.filter { it.pitch % 12 == note.pitch % 12 }.also {
                    require(it.isNotEmpty()) { "当前乐器不支持 MIDI 音高 ${note.pitch} 的半音，未自动降成自然音" }
                }
            }
        // First seek a single octave transposition for the whole melody. In-range
        // material stays unchanged. Only unavoidably wide ranges need local folding.
        val shifts = (-10..10).sortedBy { abs(it) }
        val exact = shifts.firstOrNull { shift -> notes.indices.all { i -> candidates[i].any { it.pitch == notes[i].pitch + shift * 12 } } }
        val shift =
            exact ?: shifts.minBy { octave ->
                notes.indices.sumOf { i ->
                    candidates[i].minOf { key ->
                        val distance = (key.pitch - notes[i].pitch - octave * 12).toDouble()
                        distance * distance
                    }
                } + abs(octave) * 0.01
            }
        val costs = candidates.map { DoubleArray(it.size) { Double.POSITIVE_INFINITY } }
        val parents = candidates.map { IntArray(it.size) { -1 } }
        notes.indices.forEach { i ->
            candidates[i].forEachIndexed { j, key ->
                val displacement = (key.pitch - notes[i].pitch - shift * 12).toDouble()
                val local = displacement * displacement
                if (i == 0) {
                    costs[i][j] = local
                } else {
                    candidates[i - 1].forEachIndexed { k, previous ->
                        val intervalError = ((key.pitch - previous.pitch) - (notes[i].pitch - notes[i - 1].pitch)).toDouble()
                        val cost = costs[i - 1][k] + local + intervalError * intervalError * 2
                        if (cost < costs[i][j]) {
                            costs[i][j] = cost
                            parents[i][j] = k
                        }
                    }
                }
            }
        }
        var at = costs.last().indices.minBy { costs.last()[it] }
        val result = arrayOfNulls<HarmonicaKey>(notes.size)
        for (i in notes.indices.reversed()) {
            result[i] = candidates[i][at]
            at = parents[i][at]
        }
        val mapped = result.map { it!! }
        return MappedPitches(mapped, shift, mapped.indices.count { mapped[it].pitch != notes[it].pitch + shift * 12 })
    }
}

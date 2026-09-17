package com.example.autoplaymusic.core.score
import com.example.autoplaymusic.core.harmonica.HarmonicaCompiler
import com.example.autoplaymusic.profile.GameProfiles
import dev.atsushieno.ktmidi.Midi1CompoundMessage
import dev.atsushieno.ktmidi.Midi1Music
import dev.atsushieno.ktmidi.read
import java.util.ArrayDeque

/** Reads musical data only. No game keys, octave folding or chord deletion here. */
object MidiParser {
    data class Note(
        val pitch: Int,
        val start: Long,
        val end: Long,
        val velocity: Int = 100,
        val channel: Int = 0,
        val keyEnd: Long = end,
    )

    data class Track(
        val name: String,
        val notes: List<Note>,
        val channel: Int = 0,
        val program: Int = 0,
    )

    data class Tempo(
        val tick: Long,
        val micros: Int,
    )

    data class Document(
        val tracks: List<Track>,
        val tempos: List<Tempo>,
        val resolution: Int,
        val endTick: Long = tracks.flatMap { it.notes }.maxOfOrNull { it.end } ?: 0,
        val warnings: List<String> = emptyList(),
    )

    data class Result(
        val events: List<NoteEvent>,
        val bpm: Int,
        val noteCount: Int,
        val octaveShift: Int,
        val reducedNotes: Int,
    ) {
        val durationMs get() = events.sumOf { it.beats } * 60000 / bpm
    }

    private data class Held(
        val tick: Long,
        val velocity: Int,
    )

    fun read(bytes: ByteArray): Document {
        require(bytes.size in 14..(4 * 1024 * 1024)) { "MIDI 文件为空或超过 4 MB" }
        require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(77, 84, 104, 100))) { "MIDI 文件头无效" }
        val music = Midi1Music().apply { read(bytes.toList()) }
        require(music.format.toInt() in 0..1) { "支持 MIDI 格式 0 和 1" }
        require(music.deltaTimeSpec > 0) { "暂不支持 SMPTE 时基" }
        val tempos = mutableListOf(Tempo(0, 500000))
        val tracks = mutableListOf<Track>()
        val warnings = linkedSetOf<String>()
        var endTick = 0L
        music.tracks.forEachIndexed { trackIndex, track ->
            var tick = 0L
            var name = "音轨 ${trackIndex + 1}"
            val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Held>>()
            val sustained = mutableMapOf<Int, MutableList<Note>>()
            val sustain = BooleanArray(16)
            val programs = IntArray(16)
            val notes = mutableMapOf<Int, MutableList<Note>>()

            fun emit(note: Note) {
                if (note.end > note.start) notes.getOrPut(note.channel) { mutableListOf() } += note
            }

            fun releasePedal(channel: Int) {
                sustained.remove(channel)?.forEach { emit(it.copy(end = tick.coerceAtLeast(it.keyEnd))) }
            }

            fun releaseKey(
                channel: Int,
                pitch: Int,
                held: Held,
                pedal: Boolean,
            ) {
                val note = Note(pitch, held.tick, tick, held.velocity, channel, tick)
                if (pedal) sustained.getOrPut(channel) { mutableListOf() } += note else emit(note)
            }
            track.events.forEach { event ->
                require(event.deltaTime >= 0) { "MIDI 事件时间无效" }
                tick += event.deltaTime
                val message = event.message
                val status = message.statusCode.toInt() and 255
                val channel = message.channel.toInt() and 15
                val first = message.msb.toInt() and 127
                val second = message.lsb.toInt() and 127
                when {
                    status == 255 && message is Midi1CompoundMessage -> {
                        val data = message.extraData ?: byteArrayOf()
                        val offset = message.extraDataOffset
                        if (first == 0x51 && message.extraDataLength == 3) {
                            val tempo =
                                ((data[offset].toInt() and 255) shl 16) or
                                    ((data[offset + 1].toInt() and 255) shl 8) or (data[offset + 2].toInt() and 255)
                            require(tempo > 0) { "MIDI 速度无效" }
                            tempos += Tempo(tick, tempo)
                        } else if (first == 3) {
                            name =
                                data
                                    .copyOfRange(offset, offset + message.extraDataLength)
                                    .toString(Charsets.UTF_8)
                                    .trim()
                                    .ifBlank { name }
                        }
                    }

                    status == 0xc0 -> {
                        programs[channel] = first
                    }

                    status == 0xe0 && (first or (second shl 7)) != 8192 -> {
                        warnings += "含弯音；口风琴仅支持离散半音"
                    }

                    status == 0xb0 -> {
                        when (first) {
                            64 -> {
                                sustain[channel] = second >= 64
                                if (!sustain[channel]) releasePedal(channel)
                            }

                            120, 123 -> {
                                active.filterKeys { it.first == channel }.forEach { (key, held) ->
                                    while (held.isNotEmpty()) {
                                        releaseKey(
                                            channel,
                                            key.second,
                                            held.removeFirst(),
                                            first == 123 && sustain[channel],
                                        )
                                    }
                                }
                                if (first == 120) releasePedal(channel)
                            }

                            121 -> {
                                sustain[channel] = false
                                releasePedal(channel)
                            }
                        }
                    }

                    status == 0x90 && second > 0 -> {
                        // A new strike of the same piano key ends the previous pedal tail.
                        sustained[channel]?.let { pending ->
                            pending.filter { it.pitch == first }.forEach { emit(it.copy(end = tick)) }
                            pending.removeAll { it.pitch == first }
                        }
                        active.getOrPut(channel to first) { ArrayDeque() }.addLast(Held(tick, second))
                    }

                    status == 0x80 || status == 0x90 -> {
                        active[channel to first]?.pollFirst()?.let { releaseKey(channel, first, it, sustain[channel]) }
                    }
                }
            }
            active.forEach { (key, held) -> held.forEach { releaseKey(key.first, key.second, it, false) } }
            sustained.keys.toList().forEach(::releasePedal)
            endTick = maxOf(endTick, tick)
            notes.toSortedMap().forEach { (channel, list) ->
                if (list.isNotEmpty()) {
                    tracks +=
                        Track(
                            if (notes.size > 1) "$name · 通道 ${channel + 1}" else name,
                            list.sortedWith(compareBy<Note> { it.start }.thenByDescending { it.pitch }),
                            channel,
                            programs[channel],
                        )
                }
            }
        }
        require(tracks.isNotEmpty()) { "文件没有音符" }
        require(tracks.sumOf { it.notes.size } <= 100000) { "MIDI 音符过多" }
        return Document(tracks, tempos.sortedBy { it.tick }, music.deltaTimeSpec, endTick, warnings.toList())
    }

    /** Kept for existing callers; all conversion now uses the same compiler. */
    fun convert(
        document: Document,
        trackIndex: Int,
    ): Result {
        val compiled = HarmonicaCompiler.compile(document, listOf(trackIndex), GameProfiles.harmonica8, smoothTransitions = false)
        return Result(
            compiled.toLegacyEvents(),
            compiled.bpm,
            compiled.notes.size,
            compiled.octaveShift,
            compiled.sourceNoteCount - compiled.notes.size,
        )
    }
}

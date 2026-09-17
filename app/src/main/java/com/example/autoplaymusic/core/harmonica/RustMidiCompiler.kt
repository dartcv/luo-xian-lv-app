package com.example.autoplaymusic.core.harmonica
import android.util.Base64
import com.example.autoplaymusic.core.score.NoteEvent
import com.example.autoplaymusic.core.score.PlayMode
import com.example.autoplaymusic.core.score.ScoreParser
import org.json.JSONObject

data class RustCompiledMidi(
    val score: String,
    val bpm: Int,
    val noteCount: Int,
)

object RustMidiCompiler {
    const val VERSION = 4

    fun compile(
        bytes: ByteArray,
        track: Int? = null,
        melody: Boolean = true,
    ): RustCompiledMidi? =
        runCatching {
            val request =
                JSONObject()
                    .put("midi", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .put("melody", melody)
                    .apply {
                        if (track != null) {
                            put("tracks", org.json.JSONArray().put(track))
                        } else {
                            put("tracks", org.json.JSONArray())
                        }
                    }
            val root = JSONObject(MidiCoreBridge.compileMIDIJSON(request.toString()))
            require(!root.has("error")) {
                root.optJSONObject("error")?.optString("message")
                    ?: root.optString("error", "Rust MIDI 编译失败")
            }
            val bpm = root.optInt("bpm", 120).coerceIn(20, 400)
            val notes = root.optJSONArray("notes") ?: error("Rust core 没有音符")
            val events = mutableListOf<NoteEvent>()
            var cursor = 0L
            for (i in 0 until notes.length()) {
                val note = notes.getJSONObject(i)
                val start = note.optLong("startUs")
                val end = note.optLong("releaseUs", note.optLong("endUs"))
                if (start > cursor) events += NoteEvent.rest((start - cursor) * bpm / 60_000_000.0)
                val mode =
                    when (note.optString("mode")) {
                        "升调" -> PlayMode.RAISE
                        "降调" -> PlayMode.LOWER
                        else -> PlayMode.NATURAL
                    }
                events +=
                    NoteEvent(
                        note.optInt("key", 0),
                        mode,
                        ((end - start).coerceAtLeast(1L)) * bpm / 60_000_000.0,
                        halfTone = note.optBoolean("halfTone", false),
                    )
                cursor = end
            }
            require(events.isNotEmpty()) { "Rust core 没有可播放音符" }
            RustCompiledMidi(ScoreParser.format(events, bpm), bpm, notes.length())
        }.getOrNull()
}

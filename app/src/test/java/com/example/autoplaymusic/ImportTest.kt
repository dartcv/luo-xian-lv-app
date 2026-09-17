package com.example.autoplaymusic
import com.example.autoplaymusic.core.playback.PlaybackTimeline
import com.example.autoplaymusic.core.score.MidiParser
import com.example.autoplaymusic.core.score.PlayMode
import com.example.autoplaymusic.core.score.ScoreParser
import org.junit.Assert.*
import org.junit.Test

class ImportTest {
    @Test fun durationsAndModesSurviveTextRoundTrip() {
        val source = "tempo=120 unit=0.5 0:1.25 [#1]:2.5 (7:0.125) 【#2:0.75】 rest:0.1 #5"
        val events = ScoreParser.parse(source)
        assertEquals(6, events.size)
        assertEquals(1.25, events[0].beats, 0.000001)
        assertEquals(PlayMode.RAISE, events[1].mode)
        assertTrue(events[1].halfTone)
        assertEquals(2.5, events[1].beats, 0.000001)
        assertEquals(PlayMode.LOWER, events[2].mode)
        assertTrue(events[4].rest)
        assertEquals(events, ScoreParser.parse(ScoreParser.format(events, 120)))
    }

    @Test fun midiTempoChangesAndRestsKeepTheirTiming() {
        // PPQ 480: C#5 0..240, rest 240..720, B3 720..960; tempo changes at tick 480.
        val hex =
            "4d546864000000060000000101e04d54726b00000026" +
                "00ff510307a1200090496481708049008170ff51030f42408170903b648170803b0000ff2f00"
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = MidiParser.convert(MidiParser.read(bytes), 0)
        assertEquals(3, result.events.size)
        assertEquals(PlayMode.RAISE, result.events[0].mode)
        assertTrue(result.events[0].halfTone)
        assertEquals(PlayMode.LOWER, result.events[2].mode)
        val timeline = PlaybackTimeline(result.events, result.bpm)
        assertEquals(250.0, timeline.offsets[1], 0.001)
        assertTrue(result.events[1].rest)
        assertEquals(1000.0, timeline.offsets[2], 0.001)
        assertEquals(1500L, timeline.durationMs)
    }

    @Test fun userMidiFilesMatchIndependentJdkReference() {
        listOf("rain.mid", "dan.mid").forEach { filename ->
            val bytes = javaClass.getResourceAsStream("/$filename")!!.use { it.readBytes() }
            // Reference values obtained with JDK MidiSystem, independently of ktmidi.
            val count = if (filename == "rain.mid") 107 else 171
            val durationUs = if (filename == "rain.mid") 55319160L else 133776041L
            val document = MidiParser.read(bytes)
            assertEquals(count, document.tracks.sumOf { it.notes.size })
            document.tracks.forEachIndexed { i, _ ->
                val result = MidiParser.convert(document, i)
                val restored = ScoreParser.parse(ScoreParser.format(result.events, result.bpm))
                assertEquals(result.events, restored)
                assertEquals(durationUs / 1000.0, result.durationMs, 1.0)
                println(
                    "$filename: notes=${result.noteCount}, bpm=${result.bpm}, durationMs=${result.durationMs}, rests=${result.events.count {
                        it.rest
                    }}, octaveShift=${result.octaveShift}",
                )
            }
        }
    }

    @Test fun seekUsesTimeIncludingSilence() {
        val timeline = PlaybackTimeline(ScoreParser.parse("1:1 0:2 [#2:1]"), 120)
        assertEquals(0, timeline.indexAt(0))
        assertEquals(1, timeline.indexAt(500))
        assertEquals(1, timeline.indexAt(1499))
        assertEquals(2, timeline.indexAt(1500))
        assertEquals(3, timeline.indexAt(2000))
    }
}

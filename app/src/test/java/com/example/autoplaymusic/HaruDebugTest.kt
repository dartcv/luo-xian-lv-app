package com.example.autoplaymusic
import com.example.autoplaymusic.core.harmonica.HarmonicaCompiler
import com.example.autoplaymusic.core.score.MidiParser
import com.example.autoplaymusic.core.score.MidiVoice
import com.example.autoplaymusic.core.score.ScoreParser
import com.example.autoplaymusic.profile.GameProfiles
import org.junit.Assert.*
import org.junit.Test

class HaruDebugTest {
    @Test fun inspect() {
        val bytes = javaClass.getResourceAsStream("/haruhikage.mid")!!.use { it.readBytes() }
        val doc = MidiParser.read(bytes)
        assertEquals(1, doc.tracks.size)
        assertEquals(
            1173,
            doc.tracks
                .single()
                .notes.size,
        )
        doc.tracks.forEachIndexed { i, t ->
            val r = runCatching { MidiParser.convert(doc, i) }
            println(
                "track[$i] ${t.name}: notes=${t.notes.size} range=${t.notes.minOfOrNull {
                    it.pitch
                }}..${t.notes.maxOfOrNull {
                    it.pitch
                }} result=${r.getOrNull()?.let {
                    "events=${it.events.size}, reduced=${it.reducedNotes}, bpm=${it.bpm}"
                } ?: r.exceptionOrNull()?.message}",
            )
            assertTrue(r.isSuccess)
            val result = r.getOrThrow()
            assertTrue(result.events.isNotEmpty())
            assertTrue(result.events.filterNot { it.rest }.all { it.keyIndex in 0..7 })
            assertEquals(result.events, ScoreParser.parse(ScoreParser.format(result.events, result.bpm)))
        }
        val compiled = HarmonicaCompiler.compile(doc, listOf(0), GameProfiles.harmonica8)
        println("melody events=${compiled.notes.size} grouped=${compiled.groupedOnsets} source=${compiled.sourceNoteCount}")
        val onsetCompiled = HarmonicaCompiler.compile(doc, listOf(0), GameProfiles.harmonica8, MidiVoice.ONSETS)
        println("onsets events=${onsetCompiled.notes.size} grouped=${onsetCompiled.groupedOnsets} source=${onsetCompiled.sourceNoteCount}")
        assertEquals("harmonica-8-v1", compiled.profileId)
        assertTrue(compiled.notes.isNotEmpty())
        assertTrue(compiled.notes.all { it.primaryKey in 0..7 })
        assertTrue(compiled.notes.all { it.endUs > it.startUs })
        val rain = MidiParser.read(javaClass.getResourceAsStream("/rain.mid")!!.use { it.readBytes() })
        val rainTrack = rain.tracks.indices.maxByOrNull { rain.tracks[it].notes.size }!!
        val rainCompiled = HarmonicaCompiler.compile(rain, listOf(rainTrack), GameProfiles.harmonica8)
        assertTrue(rainCompiled.notes.isNotEmpty())
        assertTrue(rainCompiled.durationMs > 0)
    }
}

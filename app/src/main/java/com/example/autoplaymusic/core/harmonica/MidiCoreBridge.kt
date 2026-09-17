package com.example.autoplaymusic.core.harmonica

/** Rust core JNI boundary. The app keeps UI and gestures in Kotlin. */
object MidiCoreBridge {
    init {
        System.loadLibrary("luoxianlv_midi_core")
    }

    external fun compileMIDIJSON(requestJson: String): String
}

package com.example.autoplaymusic.profile

/** Describes a game's playable instrument, independent of screen coordinates. */
data class GameProfile(
    val id: String,
    val keyCount: Int,
    val minMidi: Int,
    val maxMidi: Int,
    val hasSemitone: Boolean,
    val rows: Int = 1,
    val pitchVersion: Int = 1,
    val sustainRatio: Float = 1f,
    val transitionTailMs: Long = 35L,
    /** Game instruments have no phrase-level diminuendo; cap one key hold. */
    val maxHoldMs: Long = 900L,
) {
    init {
        require(keyCount > 0)
        require(minMidi <= maxMidi)
        require(sustainRatio.isFinite() && sustainRatio > 0f && sustainRatio <= 1f)
        require(transitionTailMs in 0L..80L)
        require(maxHoldMs in 80L..5000L)
    }
}

object GameProfiles {
    val harmonica8 =
        GameProfile(
            id = "harmonica-8-v1",
            keyCount = 8,
            minMidi = 48,
            maxMidi = 85,
            hasSemitone = true,
            pitchVersion = 1,
        )
}

package cx.m42.english.learn

/** Saying an English sentence aloud. The one thing in this app each platform does its own way. */
internal interface Speech {
    /** False where nothing can speak; the card says so instead of staying silent. */
    val available: Boolean

    /** Says [text], cutting off whatever was being said, and returns once it has been said. */
    suspend fun speak(text: String)

    fun stop()

    /** Lets go of what speaking holds, when the app closes; the next [speak] takes it again. */
    fun release() = stop()
}

internal expect fun platformSpeech(): Speech

/** How fast a phrase is said, against the platform's own pace: a little slower, to be repeated after. */
internal const val SpeechPace = 0.85f

/** How high a voice goes, against its own pitch: three, so that one voice still sounds like more than one person. */
internal val Pitches = listOf(1.0f, 0.9f, 1.1f)

/** Who says a phrase: which of the voices there are, and how high. */
internal data class Timbre(val voice: Int, val pitch: Float)

/**
 * The timbre of [text] among [voices] voices. The same phrase always has the same one, so saying it again sounds the
 * same, and most of the phrases around it have another, so that they seem to be said by different people.
 */
internal fun timbreFor(text: String, voices: Int): Timbre {
    val hash = stableHash(text)
    val count = voices.coerceAtLeast(1)
    return Timbre(hash.mod(count), Pitches[(hash / count).mod(Pitches.size)])
}

/** FNV-1a over the characters: `hashCode` may differ between platforms, and a phrase is to sound the same on all. */
private fun stableHash(text: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (char in text) {
        hash = hash xor char.code
        hash *= 0x01000193
    }
    return hash
}

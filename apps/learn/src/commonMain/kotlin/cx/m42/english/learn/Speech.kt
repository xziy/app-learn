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

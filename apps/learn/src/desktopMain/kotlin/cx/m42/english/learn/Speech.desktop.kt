package cx.m42.english.learn

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal actual fun platformSpeech(): Speech = ProcessSpeech { text -> speechCommand(text) }

/** The synthesizers a desktop may have, best known first; the text is the last argument of each. */
private val Engines = listOf(
    listOf("say", "-v", "Samantha"),
    listOf("espeak-ng", "-v", "en-us", "-s", "145"),
    listOf("espeak", "-v", "en-us", "-s", "145"),
    listOf("spd-say", "-l", "en", "-w"),
)

/** Looked up once, on the first need: reading PATH is not for the moment the host starts. */
private val engine: List<String>? by lazy {
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
    Engines.firstOrNull { engine -> path.any { File(it, engine.first()).canExecute() } }
}

private fun speechCommand(text: String): List<String>? = engine?.plus(text)

/**
 * Speech as a process per sentence. The text goes as one argument with no shell in between. [command]
 * gives what to run, or null when there is nothing on this computer that can speak.
 */
internal class ProcessSpeech(private val command: (text: String) -> List<String>?) : Speech {
    private var running: Process? = null

    override val available: Boolean get() = command("") != null

    override suspend fun speak(text: String) {
        stop()
        val line = command(text) ?: return
        val process = runCatching {
            ProcessBuilder(line)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: return
        running = process
        // Interruptible: being cancelled does not wait for the end of the sentence.
        runInterruptible(Dispatchers.IO) { process.waitFor() }
    }

    override fun stop() {
        running?.destroy()
        running = null
    }
}

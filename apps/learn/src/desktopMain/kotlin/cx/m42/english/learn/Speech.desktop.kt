package cx.m42.english.learn

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal actual fun platformSpeech(): Speech = ProcessSpeech { text -> engine?.let { speechCommand(it, text) } }

/** The synthesizers a desktop may have, best known first. */
private val Programs = listOf("say", "espeak-ng", "espeak", "spd-say")

/** Looked up once, on the first need: reading PATH is not for the moment the host starts. */
private val engine: String? by lazy {
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
    Programs.firstOrNull { program -> path.any { File(it, program).canExecute() } }
}

/** espeak's own variants of a voice, men's and women's in turn. Every install has them. */
internal val EspeakVariants = listOf("m3", "f2", "m1", "f3", "m4", "f4")

/** The kinds of voice speech-dispatcher knows; the module picks its own voice of each kind. */
internal val DispatcherVoices = listOf("male1", "female1", "male2", "female2", "male3", "female3")

/** The macOS voices to take turns, where installed: American first, then the others that read English well. */
private val MacVoices = listOf("Samantha", "Alex", "Daniel", "Karen", "Moira", "Tessa", "Fred", "Victoria")

/** Those of [MacVoices] this Mac has, asked once. Samantha alone if it cannot be asked. */
private val macVoices: List<String> by lazy {
    val installed = runCatching {
        val process = ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start()
        val listing = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        listing.lines().mapNotNull { line -> line.trim().split(Regex("""\s{2,}""")).firstOrNull()?.takeIf { it.isNotEmpty() } }.toSet()
    }.getOrDefault(emptySet())
    MacVoices.filter { it in installed }.ifEmpty { listOf("Samantha") }
}

/**
 * What to run to say [text] with [program]: a little slower than its own pace and in the timbre of the phrase, a voice
 * of its own and a pitch. The text is the last argument.
 */
internal fun speechCommand(program: String, text: String, sayVoices: () -> List<String> = { macVoices }): List<String> =
    when (program) {
        "say" -> {
            val voices = sayVoices()
            // About 175 words a minute is its own pace.
            listOf("say", "-v", voices[timbreFor(text, voices.size).voice], "-r", "${(175 * SpeechPace).roundToInt()}", text)
        }
        "spd-say" -> {
            val timbre = timbreFor(text, DispatcherVoices.size)
            // Its rate and pitch go from -100 to 100, its own pace being 0.
            listOf(
                "spd-say", "-l", "en", "-w", "-t", DispatcherVoices[timbre.voice],
                "-r", "${((SpeechPace - 1) * 100).roundToInt()}", "-p", "${((timbre.pitch - 1) * 100).roundToInt()}", text,
            )
        }
        else -> {
            val timbre = timbreFor(text, EspeakVariants.size)
            // Its own pace is 175 words a minute and this app said 145; now it is 128. A pitch of 50 is its own.
            listOf(
                program, "-v", "en-us+${EspeakVariants[timbre.voice]}",
                "-s", "${(150 * SpeechPace).roundToInt()}", "-p", "${(50 * timbre.pitch).roundToInt()}", text,
            )
        }
    }

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

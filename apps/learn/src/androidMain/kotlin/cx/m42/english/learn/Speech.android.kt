package cx.m42.english.learn

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as EngineVoice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

internal actual fun platformSpeech(): Speech = AndroidSpeech { LearnContextProvider.app }

/**
 * The application's Context, taken as the process starts. An app sees only its `AppRuntime`, and the host
 * keeps its own Context to itself (`AndroidHost`), so the module brings its own way in: a provider in its
 * manifest, which lands in the manifest of whichever host includes the module. It answers no queries.
 */
internal class LearnContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        app = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        @Volatile
        var app: Context? = null
    }
}

/**
 * The phone's own synthesizer, `TextToSpeech`: whichever engine is set in the system, Google's on most phones.
 * It binds to the engine's service, so it is made on the first need rather than when the host starts, and let
 * go when the app closes ([release]); the next need makes a new one.
 */
private class AndroidSpeech(private val context: () -> Context?) : Speech {
    private val lock = Any()
    private var engine: Engine? = null

    /**
     * True until the engine is known to have no English. It answers a moment after it is made, and the card
     * that asked first already shows the button; if the engine then has no English, that button says nothing
     * and the next card says it cannot speak.
     */
    override val available: Boolean
        get() {
            val engine = engine() ?: return false
            return engine.speaksEnglish != false
        }

    override suspend fun speak(text: String) {
        stop()
        engine()?.say(text)
    }

    override fun stop() {
        // Not engine(): stopping is no reason to bind one.
        synchronized(lock) { engine }?.stop()
    }

    override fun release() {
        val engine = synchronized(lock) { engine.also { engine = null } }
        engine?.shutdown()
    }

    private fun engine(): Engine? = synchronized(lock) {
        engine ?: context()?.let(::Engine)?.also { engine = it }
    }
}

/** English as the phone has it, best first: an engine may have only one of them. */
private val Englishes = listOf(Locale.US, Locale.UK, Locale.ENGLISH)

/** One binding to the engine. Each sentence has its own id, so the end of the one before is told apart. */
private class Engine(context: Context) : UtteranceProgressListener() {
    private val sentences = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val next = AtomicInteger()
    private val answered = CompletableDeferred<Int>()

    /**
     * The status the engine answered with. Written by the callback alone: a failure comes inside the
     * constructor of [tts], before the field is set, so the callback must not touch it.
     */
    @Volatile
    private var status: Int? = null

    private val tts = TextToSpeech(context) { status ->
        this.status = status
        answered.complete(status)
    }.also { it.setOnUtteranceProgressListener(this) }

    /** Asked once, after a successful answer: it chooses the language too. */
    private val english: Boolean by lazy {
        Englishes.any { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE && tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
    }

    /**
     * The engine's voices of the English it chose, to take turns: Google's has several men and women for each. Only
     * those on the phone, so that a phrase is said without a network, and in the order of their names, so that a phrase
     * has the same voice every time.
     */
    private val voices: List<EngineVoice> by lazy {
        val chosen = tts.voice?.locale
        runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { voice ->
                chosen != null && voice.locale.language == chosen.language && voice.locale.country == chosen.country &&
                    !voice.isNetworkConnectionRequired &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            .sortedBy { it.name }
    }

    /** Null while the engine has not answered yet. */
    val speaksEnglish: Boolean?
        get() = when (status) {
            null -> null
            TextToSpeech.SUCCESS -> english
            else -> false
        }

    suspend fun say(text: String) {
        if (answered.await() != TextToSpeech.SUCCESS || !english) return
        val id = "learn-${next.incrementAndGet()}"
        val timbre = timbreFor(text, voices.size)
        voices.getOrNull(timbre.voice)?.let { tts.setVoice(it) }
        tts.setSpeechRate(SpeechPace)
        tts.setPitch(timbre.pitch)
        suspendCancellableCoroutine { sentence ->
            sentences[id] = sentence
            sentence.invokeOnCancellation { sentences.remove(id) }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) over(id)
        }
    }

    fun stop() {
        // An engine that has not answered has nothing to stop, and only logs an error if asked.
        if (status == TextToSpeech.SUCCESS) tts.stop()
        // An engine tells about the stopped sentence late, or not at all.
        sentences.keys.toList().forEach(::over)
    }

    fun shutdown() {
        stop()
        // A sentence still waiting for the engine to answer gives up.
        answered.complete(TextToSpeech.ERROR)
        tts.shutdown()
    }

    private fun over(id: String?) {
        sentences.remove(id ?: return)?.resume(Unit)
    }

    // Nullable: the engine is another app, and a null from it must not crash this one.
    override fun onStart(utteranceId: String?) {}

    override fun onDone(utteranceId: String?) = over(utteranceId)

    override fun onStop(utteranceId: String?, interrupted: Boolean) = over(utteranceId)

    override fun onError(utteranceId: String?, errorCode: Int) = over(utteranceId)

    @Deprecated("Engines before API 21 call only this one.")
    override fun onError(utteranceId: String?) = over(utteranceId)
}

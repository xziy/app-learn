package cx.m42.english.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the feed opens: a page and, on a phrase, its step. */
internal data class Start(val page: Int, val step: Int = 0)

/**
 * What the person has done with the words, by the key of a word's sense: seen in an English phrase, in the order that
 * happened, and known, as they said. Neither is a theme's own: a word is the same word in all of them.
 */
internal data class WordMarks(val seen: List<String> = emptyList(), val known: Set<String> = emptySet()) {
    val seenSet: Set<String> by lazy { seen.toSet() }
}

/** The chosen verbs and the feed through them. */
internal data class Session(
    /** Every theme there is, to choose from. */
    val themes: List<Theme>,
    /** The one all the rest belongs to. */
    val theme: Theme,
    /** Every verb of the theme. */
    val all: List<String>,
    val chosen: List<String>,
    /** The chosen ones that could be read. */
    val verbs: List<Verb>,
    /** The verbs gone through to their end, in the order that happened. */
    val seen: List<String>,
    val pages: List<FeedPage>,
    /** The furthest tense each verb not yet seen has come to, by [Tense.key]: where the verb opens again. */
    val progress: Map<String, String> = emptyMap(),
) {
    /** What can still be chosen. */
    val others: List<String> get() = all - chosen.toSet() - seen.toSet()

    /** The feed of one verb alone, to watch it again. */
    fun only(verb: Verb): Session = copy(verbs = listOf(verb), pages = buildPages(listOf(verb)))

    fun firstPageOf(word: String): Int = pages.indexOfFirst { it.verb.word == word }

    /**
     * Where a verb opens from the list: the cover of the furthest tense it has come to, so a tense left on its last card
     * is gone through again from its start. A verb that has not come to a tense yet opens on its own card.
     */
    fun startOf(word: String): Int {
        val first = firstPageOf(word)
        val tense = progress[word] ?: return first
        val cover = pages.indexOfFirst { it is FeedPage.TenseCard && it.verb.word == word && it.tense.key == tense }
        return if (cover >= 0) cover else first
    }

    /** Which of its verb's pages this one is, counted from 1, and how many the verb has. */
    fun inVerb(page: Int): Pair<Int, Int> {
        val word = pages[page].verb.word
        val first = firstPageOf(word)
        return page - first + 1 to pages.indexOfLast { it.verb.word == word } - first + 1
    }
}

/** `verb0:active.simple.future,verb3:passive` as verb → tense key. Whatever cannot be read is left out. */
internal fun readProgress(stored: String?): Map<String, String> =
    stored.orEmpty().split(',').mapNotNull { entry ->
        val word = entry.substringBefore(':', "")
        val tense = entry.substringAfter(':', "")
        if (word.isEmpty() || tense.isEmpty()) null else word to tense
    }.toMap()

/** Whether [tense] is further on in this verb than the one [kept] as its progress; one it no longer has counts as none. */
internal fun Verb.further(tense: Tense, kept: String?): Boolean =
    tenses.indexOfFirst { it.key == tense.key } > tenses.indexOfFirst { it.key == kept }

internal sealed interface LearnState {
    data object Loading : LearnState
    data object Empty : LearnState
    data class Ready(val session: Session) : LearnState
}

/**
 * One open language. The theme, and the chosen verbs, the seen verbs, the place in the feed and the progress of each verb
 * of each theme, live in `runtime.storage` rather than in a snapshot: the place is written on every card, so it survives
 * a closed window as well as a killed process.
 */
internal class LearnInstance(
    private val runtime: InstanceRuntime,
    private val source: VerbSource,
    private val speech: Speech,
    private val shuffle: (List<String>) -> List<String>,
    private val sharing: Sharing,
) : AppInstance() {

    override val chrome: StateFlow<AppChrome> = MutableStateFlow(AppChrome(fullscreen = true))

    private val _state = MutableStateFlow<LearnState>(LearnState.Loading)
    val state: StateFlow<LearnState> get() = _state.asStateFlow()

    val canSpeak: Boolean get() = speech.available

    /** Whether [share] opens the system's menu, or only copies the text where there is none. */
    val sharesViaMenu: Boolean get() = sharing.viaMenu

    /** Hands what was asked about a phrase to the system. False if it could not be done. */
    fun share(text: String): Boolean = sharing.share(text)

    private val _lexicon = MutableStateFlow<Lexicon?>(null)

    /** Null until it is read, empty if the build has none. */
    val lexicon: StateFlow<Lexicon?> get() = _lexicon.asStateFlow()

    private val _marks = MutableStateFlow(WordMarks())
    val marks: StateFlow<WordMarks> get() = _marks.asStateFlow()

    /** `verb:page in the verb:step`: by the verb, so choosing other verbs does not move it. */
    private var place: String? = null

    /** The load under way: a later one replaces it, so what shows is what was asked for last. */
    private var loading: Job? = null

    override suspend fun onLaunch() {
        runtime.analytics.screen("learn")
        // After the first frame: reading the base is not for `onLaunch`.
        reload {}
        runtime.scope.launch {
            _marks.value = WordMarks(
                seen = runtime.storage.get(WORDS_SEEN)?.split(',')?.filter { it.isNotEmpty() }.orEmpty(),
                known = runtime.storage.get(WORDS_KNOWN)?.split(',')?.filter { it.isNotEmpty() }.orEmpty().toSet(),
            )
            val nouns = readTable("the dictionary", Lexicon.Empty) { source.lexicon()?.let(::parseLexicon) }
            val grammar = GrammarWords(
                service = readTable("the service words", emptyList()) { source.serviceWords()?.let(::parseServiceWords) },
                irregular = readTable("the irregular verbs", emptyList()) { source.irregularVerbs()?.let(::parseIrregularVerbs) },
            )
            _lexicon.value = Lexicon(nouns.entries, grammar)
        }
    }

    /** One table that cannot be read costs its own words, not the others. */
    private suspend fun <T> readTable(name: String, none: T, read: suspend () -> T?): T =
        try {
            read() ?: none
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.logger.error("Cannot read $name: ${e.message}")
            none
        }

    /** The English of a phrase has been on screen: its nouns are seen, those seen before move to the end. */
    fun saw(nouns: List<NounUse>) {
        val keys = nouns.map { it.key }.distinct()
        if (keys.isEmpty()) return
        val marks = _marks.value
        if (marks.seen.takeLast(keys.size) == keys) return
        val seen = marks.seen.filterNot { it in keys } + keys
        _marks.value = marks.copy(seen = seen)
        runtime.scope.launch { runtime.storage.set(WORDS_SEEN, seen.joinToString(",")) }
    }

    fun know(key: String, known: Boolean) {
        val marks = _marks.value
        val now = if (known) marks.known + key else marks.known - key
        if (now == marks.known) return
        _marks.value = marks.copy(known = now)
        runtime.scope.launch {
            if (now.isEmpty()) runtime.storage.remove(WORDS_KNOWN) else runtime.storage.set(WORDS_KNOWN, now.joinToString(","))
        }
    }

    /** The words seen so far are forgotten; what is known stays known. */
    fun forgetSeen() {
        _marks.value = _marks.value.copy(seen = emptyList())
        runtime.scope.launch { runtime.storage.remove(WORDS_SEEN) }
    }

    /** A verb of any theme, for the phrases a word stands in. */
    suspend fun readIn(theme: String, word: String): Verb? =
        _state.value.let { (it as? LearnState.Ready)?.session?.themes?.find { t -> t.name == theme } }
            ?.let { readVerb(it, word) }

    private fun reload(change: suspend () -> Unit) {
        loading?.cancel()
        loading = runtime.scope.launch {
            change()
            _state.value = load()
        }
    }

    private suspend fun load(): LearnState {
        val themes = source.themes()
        val stored = runtime.storage.get(THEME)
        val theme = themes.find { it.name == stored }
            ?: themes.find { it.name == DefaultTheme }
            ?: themes.firstOrNull()
            ?: return LearnState.Empty
        val all = source.index(theme.name)
        if (all.isEmpty()) return LearnState.Empty
        val seen = runtime.storage.get(SEEN.of(theme))?.split(',')?.filter { it in all }.orEmpty()
        val round = runtime.storage.get(CHOSEN.of(theme))?.split(',')?.filter { it in all }
        val left = round.orEmpty() - seen.toSet()
        // Nothing stored, or all of it seen, is time for the next round. Nothing left because the person removed it
        // all is their choice: the list says so and has a button for it.
        val chosen = if (left.isEmpty() && round?.isEmpty() != true) draw(theme, all - seen.toSet()) else left
        place = runtime.storage.get(PLACE.of(theme))
        val verbs = chosen.mapNotNull { readVerb(theme, it) }
        val pages = buildPages(verbs)
        val kept = readProgress(runtime.storage.get(PROGRESS.of(theme))).filterKeys { it in all && it !in seen }
        // A verb left halfway before there was progress has it in the place where the feed stopped.
        val progress = kept + listOfNotNull(placedTense(pages)).filter { (word, _) -> word !in kept && word !in seen }
        if (progress != kept) writeProgress(theme, progress)
        return LearnState.Ready(Session(themes, theme, all, chosen, verbs, seen, pages, progress))
    }

    /** The verb of the stored place and the tense it is in, if it is in one. */
    private fun placedTense(pages: List<FeedPage>): Pair<String, String>? {
        val parts = place?.split(':') ?: return null
        val first = pages.indexOfFirst { it.verb.word == parts[0] }
        if (first < 0) return null
        val entry = pages.getOrNull(first + (parts.getOrNull(1)?.toIntOrNull() ?: 0))?.takeIf { it.verb.word == parts[0] }
        return entry?.tense()?.let { parts[0] to it.key }
    }

    private suspend fun writeProgress(theme: Theme, progress: Map<String, String>) {
        val key = PROGRESS.of(theme)
        if (progress.isEmpty()) {
            runtime.storage.remove(key)
        } else {
            runtime.storage.set(key, progress.entries.joinToString(",") { (word, tense) -> "$word:$tense" })
        }
    }

    /**
     * The next round, kept until it is gone through: at random from [fresh], and from [spare] if there are too few.
     * With nothing to draw from it leaves no key, so the first verb that is no longer seen starts a round by itself.
     */
    private suspend fun draw(theme: Theme, fresh: List<String>, spare: List<String> = emptyList()): List<String> {
        val round = (shuffle(fresh) + shuffle(spare)).take(WordsAtOnce)
        val key = CHOSEN.of(theme)
        if (round.isEmpty()) runtime.storage.remove(key) else runtime.storage.set(key, round.joinToString(","))
        return round
    }

    /** One broken file costs its own word, not the session. */
    private suspend fun readVerb(theme: Theme, word: String): Verb? =
        try {
            parseVerb(source.read(theme.name, word))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.logger.error("Cannot read the verb $word: ${e.message}")
            null
        }

    /** Where the person stopped, if that is still among the chosen verbs. */
    fun resume(session: Session): Start? {
        val parts = place?.split(':') ?: return null
        val word = parts[0]
        val first = session.firstPageOf(word)
        if (first < 0) return null
        val last = session.pages.indexOfLast { it.verb.word == word }
        return Start(
            page = (first + (parts.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(first, last),
            step = (parts.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, Step.entries.lastIndex),
        )
    }

    /**
     * The feed came to rest here, coming [from] that page. Past the last page there is nothing to come back to.
     * Forward out of a verb means it has been gone through to its end, and its progress is over; back into one, or
     * only a look at it, does not. A tense the verb had not come to before is its progress from now on.
     *
     * No reload: the feed is on these very pages. A seen verb leaves the chosen ones on the way back, in [refresh].
     */
    fun settled(from: Int?, page: Int, step: Int) {
        val session = (_state.value as? LearnState.Ready)?.session ?: return
        val entry = session.pages.getOrNull(page)
        val value = entry?.let { "${it.verb.word}:${page - session.firstPageOf(it.verb.word)}:$step" }
        place = value
        val key = PLACE.of(session.theme)
        runtime.scope.launch {
            if (value == null) runtime.storage.remove(key) else runtime.storage.set(key, value)
        }
        val left = from?.takeIf { it < page }?.let { session.pages.getOrNull(it) }?.verb?.word
            ?.takeIf { it != entry?.verb?.word }
        val seen = if (left != null && left !in session.seen) session.seen + left else session.seen
        var progress = if (left != null) session.progress - left else session.progress
        if (entry != null) {
            val word = entry.verb.word
            val tense = entry.tense()
            if (tense != null && word !in seen && entry.verb.further(tense, progress[word])) progress = progress + (word to tense.key)
        }
        if (seen == session.seen && progress == session.progress) return
        _state.value = LearnState.Ready(session.copy(seen = seen, progress = progress))
        runtime.scope.launch {
            if (seen != session.seen) runtime.storage.set(SEEN.of(session.theme), seen.joinToString(","))
            if (progress != session.progress) writeProgress(session.theme, progress)
        }
    }

    /** The verb opens on its own card again, from the list as well. True if it had come anywhere to start over from. */
    fun restart(word: String): Boolean {
        val session = (_state.value as? LearnState.Ready)?.session ?: return false
        if (word !in session.progress) return false
        val progress = session.progress - word
        _state.value = LearnState.Ready(session.copy(progress = progress))
        runtime.scope.launch { writeProgress(session.theme, progress) }
        return true
    }

    /** Back from the feed: what was seen on the way leaves the chosen verbs, and the next round may be due. */
    fun refresh() = reload {}

    /** It can be drawn again, and it is among the chosen again if it still was when it was seen. */
    fun unsee(word: String) = editSeen { it - word }

    fun unseeAll() = editSeen { emptyList() }

    private fun editSeen(edit: (List<String>) -> List<String>) {
        val session = (_state.value as? LearnState.Ready)?.session ?: return
        val seen = edit(session.seen)
        // At once: the next swipe can come before the load is done, and has to go on from this one.
        _state.value = LearnState.Ready(session.copy(seen = seen))
        reload { runtime.storage.set(SEEN.of(session.theme), seen.joinToString(",")) }
    }

    /** A new round in place of this one, of other verbs as far as there are any. */
    fun pickAtRandom() {
        val session = (_state.value as? LearnState.Ready)?.session ?: return
        reload { draw(session.theme, session.others, session.chosen) }
    }

    /** A seen verb is read when its row comes on screen: there can be as many of them as the theme has verbs. */
    suspend fun read(session: Session, word: String): Verb? = readVerb(session.theme, word)

    /** Each theme keeps its own verbs and its own place, so coming back to one finds it as it was left. */
    fun chooseTheme(name: String) = reload { runtime.storage.set(THEME, name) }

    fun choose(word: String) = editChosen { it + word }

    fun unchoose(word: String) = editChosen { it - word }

    private fun editChosen(edit: (List<String>) -> List<String>) {
        val session = (_state.value as? LearnState.Ready)?.session ?: return
        reload { runtime.storage.set(CHOSEN.of(session.theme), edit(session.chosen).joinToString(",")) }
    }

    /** Returns once it is said. Cancelled, it leaves nothing sounding. */
    suspend fun speak(text: String) {
        try {
            // A synthesizer that never finishes must not hold up the feed that is playing.
            withTimeoutOrNull(SPEECH_LIMIT_MILLIS) { speech.speak(text) }
        } finally {
            speech.stop()
        }
    }

    fun close() {
        speech.stop()
        runtime.scope.launch { runtime.navigation.close() }
    }

    override fun onClose() = speech.stop()

    override fun dispose() = speech.release()

    @Composable
    override fun Content() {
        // Follows the language chosen in Settings without reopening the app.
        val lang by runtime.locale.langTag.collectAsState()
        val strings = remember(lang) { LearnStrings.of(runtime.locale) }
        LearnScreen(this, strings)
    }

    private companion object {
        const val THEME = "theme"
        const val CHOSEN = "chosen"
        const val PLACE = "place"
        const val SEEN = "seen"
        /** `verb:tense key,…`, the furthest tense of each verb begun: [Session.progress]. */
        const val PROGRESS = "progress"
        const val WORDS_SEEN = "lex.seen"
        const val WORDS_KNOWN = "lex.known"

        /** Before there were themes these keys had no theme in them, and what they hold is the default theme's. */
        fun String.of(theme: Theme): String = if (theme.name == DefaultTheme) this else "$this.${theme.name}"

        /** Far more than a sentence of up to 14 words takes to say. */
        const val SPEECH_LIMIT_MILLIS = 30_000L
    }
}

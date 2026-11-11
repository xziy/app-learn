package cx.m42.english.books

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

internal const val MinFont: Int = 14
internal const val MaxFont: Int = 30
internal const val DefaultFont: Int = 19
internal const val FontStep: Int = 1

/** How the person likes to read: the same for every book. */
internal data class ReaderSettings(
    val lines: Set<Line> = setOf(Line.Ours, Line.Thought, Line.English),
    val font: Int = DefaultFont,
    val serif: Boolean = true,
    val paper: Paper = Paper.Sepia,
    val nouns: Boolean = true,
) {
    /** The lines shown for [book], in the order they are read. A book without transcription has nothing in that line. */
    fun shown(book: Book): List<Line> =
        Line.entries.filter { it in lines && (it != Line.Transcription || book.transcription) }.ifEmpty { listOf(Line.English) }

    /** One line, and the text runs on as in any book. */
    fun flowing(book: Book): Boolean = shown(book).size == 1

    /** One more line, or one less; the last one stays, since a page with no lines has nothing on it. */
    fun toggle(line: Line): ReaderSettings {
        val now = if (line in lines) lines - line else lines + line
        return if (now.isEmpty()) this else copy(lines = now)
    }
}

/** Where a book was left: the item of the reader at the top of the screen and how far it was scrolled into. */
internal data class Place(val item: Int, val offset: Int = 0) {
    fun encode(): String = "$item:$offset"

    companion object {
        fun decode(value: String?): Place? {
            val parts = value?.split(':') ?: return null
            val item = parts[0].toIntOrNull() ?: return null
            return Place(item.coerceAtLeast(0), parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
        }
    }
}

/** The reader's items: the title, then each paragraph, then the end. */
internal fun Book.itemOf(paragraph: Int): Int = paragraph + 1

/** How much of a book is read, by where it was left: 0 to 100. */
internal fun percentRead(place: Place?, paragraphs: Int): Int? {
    if (place == null || paragraphs <= 0) return null
    val passed = (place.item - 1).coerceIn(0, paragraphs)
    return (passed * 100 / paragraphs).coerceIn(0, 100)
}

internal sealed interface Shelf {
    data object Loading : Shelf
    data class Ready(val books: List<BookInfo>) : Shelf
}

internal sealed interface Opened {
    val slug: String

    data class Loading(override val slug: String) : Opened
    data class Ready(val book: Book, val start: Place?) : Opened {
        override val slug: String get() = book.slug
    }
    data class Failed(override val slug: String) : Opened
}

/**
 * The shelf and the book open on it. The settings, each book's place and the order the books were last read in live
 * in `runtime.storage` rather than in a snapshot: the place is written as the page scrolls, so it survives a closed
 * window as well as a killed process.
 */
internal class BooksInstance(
    private val runtime: InstanceRuntime,
    private val source: BookSource,
) : AppInstance() {

    override val chrome: StateFlow<AppChrome> = MutableStateFlow(AppChrome(fullscreen = true))

    private val _shelf = MutableStateFlow<Shelf>(Shelf.Loading)
    val shelf: StateFlow<Shelf> get() = _shelf.asStateFlow()

    private val _opened = MutableStateFlow<Opened?>(null)
    val opened: StateFlow<Opened?> get() = _opened.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> get() = _settings.asStateFlow()

    private val _places = MutableStateFlow<Map<String, Place>>(emptyMap())
    val places: StateFlow<Map<String, Place>> get() = _places.asStateFlow()

    /** The book being read, last first. */
    private var recent: List<String> = emptyList()

    private var opening: Job? = null

    override suspend fun onLaunch() {
        runtime.analytics.screen("books")
        // After the first frame: reading the shelf is not for `onLaunch`.
        runtime.scope.launch { load() }
    }

    private suspend fun load() {
        val storage = runtime.storage
        _settings.value = ReaderSettings(
            lines = storage.get(LINES)?.split(',')?.mapNotNull { key -> Line.entries.find { it.key == key } }?.toSet()
                ?.ifEmpty { null } ?: ReaderSettings().lines,
            font = storage.get(FONT)?.toIntOrNull()?.coerceIn(MinFont, MaxFont) ?: DefaultFont,
            serif = storage.get(SERIF) != "no",
            paper = Paper.of(storage.get(PAPER)),
            nouns = storage.get(NOUNS) != "no",
        )
        recent = storage.get(RECENT)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        val books = try {
            source.index()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.logger.error("Cannot read the shelf: ${e.message}")
            emptyList()
        }
        _places.value = books.mapNotNull { book -> Place.decode(storage.get(PLACE + book.slug))?.let { book.slug to it } }.toMap()
        _shelf.value = Shelf.Ready(ordered(books))
    }

    /** The ones read lately on top, the latest first; the others after them as the shelf lists them. */
    private fun ordered(books: List<BookInfo>): List<BookInfo> =
        books.sortedBy { book -> recent.indexOf(book.slug).let { if (it < 0) Int.MAX_VALUE else it } }

    fun open(slug: String) {
        opening?.cancel()
        _opened.value = Opened.Loading(slug)
        recent = listOf(slug) + (recent - slug)
        (_shelf.value as? Shelf.Ready)?.let { _shelf.value = Shelf.Ready(ordered(it.books)) }
        opening = runtime.scope.launch {
            runtime.storage.set(RECENT, recent.joinToString(","))
            _opened.value = try {
                Opened.Ready(parseBook(slug, source.read(slug)), _places.value[slug])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runtime.logger.error("Cannot read the book $slug: ${e.message}")
                Opened.Failed(slug)
            }
        }
    }

    /** Back to the shelf. */
    fun closeBook() {
        opening?.cancel()
        _opened.value = null
    }

    /** The reader came to rest here. */
    fun place(slug: String, place: Place) {
        if (_places.value[slug] == place) return
        _places.value = _places.value + (slug to place)
        runtime.scope.launch { runtime.storage.set(PLACE + slug, place.encode()) }
    }

    fun update(settings: ReaderSettings) {
        val before = _settings.value
        if (settings == before) return
        _settings.value = settings
        runtime.scope.launch {
            val storage = runtime.storage
            if (settings.lines != before.lines) {
                storage.set(LINES, Line.entries.filter { it in settings.lines }.joinToString(",") { it.key })
            }
            if (settings.font != before.font) storage.set(FONT, settings.font.toString())
            if (settings.serif != before.serif) storage.set(SERIF, if (settings.serif) "yes" else "no")
            if (settings.paper != before.paper) storage.set(PAPER, settings.paper.key)
            if (settings.nouns != before.nouns) storage.set(NOUNS, if (settings.nouns) "yes" else "no")
        }
    }

    fun close() {
        runtime.scope.launch { runtime.navigation.close() }
    }

    @Composable
    override fun Content() {
        // Follows the language chosen in Settings without reopening the app.
        val lang by runtime.locale.langTag.collectAsState()
        val strings = remember(lang) { BooksStrings.of(runtime.locale) }
        BooksScreen(this, strings)
    }

    private companion object {
        const val LINES = "lines"
        const val FONT = "font"
        const val SERIF = "serif"
        const val PAPER = "paper"
        const val NOUNS = "nouns"
        const val RECENT = "recent"
        const val PLACE = "place."
    }
}

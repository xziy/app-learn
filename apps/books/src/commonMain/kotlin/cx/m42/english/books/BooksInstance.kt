package cx.m42.english.books

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.DownloadTooLarge
import cx.m42.superizer.runtime.FileDownloader
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.runtime.QrScanner
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
    source: BookSource,
    /** A book's address the app was opened with, from a QR code or a link: the download screen opens with it. */
    private val initialLink: String? = null,
) : AppInstance() {

    private val folder: AppFolder? = runtime.service(AppFolder.Key)
    private val downloader: FileDownloader? = runtime.service(FileDownloader.Key)
    private val scanner: QrScanner? = runtime.service(QrScanner.Key)
    private val library = Library(source, folder, runtime.logger)

    /** Books can be added here: the host downloads and keeps files for this app. */
    val canFetch: Boolean get() = folder != null && downloader != null

    /** The host has a camera to read a book's QR code with. */
    val canScan: Boolean get() = scanner != null

    /** Where the downloaded books are, as the person finds them; null where there is no folder. */
    val folderLocation: String? get() = folder?.location?.ifEmpty { null }

    /** On iOS the folder is found in Files, under the host; elsewhere its path says where it is. */
    val folderInFiles: Boolean get() = runtime.host.platform == Platform.Ios

    private val _fetch = MutableStateFlow<Fetch?>(null)

    /** The download screen, or null when it is closed. */
    val fetch: StateFlow<Fetch?> get() = _fetch.asStateFlow()

    private var fetching: Job? = null

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
        if (initialLink != null) _fetch.value = Fetch(link = initialLink)
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
        loadShelf()
    }

    /** The shelf as it is now: after a download or a deletion, and whatever the person did in the folder meanwhile. */
    private suspend fun loadShelf(): List<BookInfo> {
        val books = library.index()
        _places.value = books.mapNotNull { book -> Place.decode(runtime.storage.get(PLACE + book.slug))?.let { book.slug to it } }.toMap()
        _shelf.value = Shelf.Ready(ordered(books))
        return books
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
                Opened.Ready(parseBook(slug, library.read(slug)), _places.value[slug])
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

    // ------------------------------------------------------------ adding a book

    fun openFetch() {
        if (_fetch.value == null) _fetch.value = Fetch()
    }

    /** Back to the shelf; a download under way is dropped. */
    fun closeFetch() {
        fetching?.cancel()
        fetching = null
        _fetch.value = null
    }

    /** A new link says nothing yet about how its download will go. */
    fun editLink(text: String) {
        val current = _fetch.value ?: return
        if (current.state == Fetching.Working) return
        _fetch.value = Fetch(link = text)
    }

    /** Reads a QR code and downloads what it names straight away: pointing the camera at it was the asking. */
    fun scan() {
        val scanner = scanner ?: return
        if (_fetch.value?.state == Fetching.Working) return
        runtime.scope.launch {
            val text = try {
                scanner.scan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runtime.logger.warn("The camera failed: ${e.message}")
                null
            } ?: return@launch
            editLink(text)
            download()
        }
    }

    fun download() {
        val current = _fetch.value ?: return
        if (current.state == Fetching.Working) return
        val url = bookLink(current.link)
        if (url == null) {
            _fetch.value = current.copy(state = Fetching.Failed(FetchProblem.NotALink))
            return
        }
        _fetch.value = current.copy(state = Fetching.Working)
        fetching = runtime.scope.launch {
            val result = fetchBook(url)
            _fetch.value = _fetch.value?.copy(state = result)
            runtime.analytics.event("book_fetch", mapOf("result" to resultName(result)))
        }
    }

    private suspend fun fetchBook(url: String): Fetching {
        val downloader = downloader ?: return Fetching.Failed(FetchProblem.Unavailable)
        val folder = folder ?: return Fetching.Failed(FetchProblem.Unavailable)
        val bytes = try {
            downloader.download(url, MAX_ARCHIVE_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadTooLarge) {
            return Fetching.Failed(FetchProblem.TooLarge)
        } catch (e: NetworkException) {
            return if (e.status == null) Fetching.Failed(FetchProblem.NoAnswer) else Fetching.Failed(FetchProblem.Status, e.status)
        } catch (e: Exception) {
            runtime.logger.warn("Download failed: $url: ${e.message}")
            return Fetching.Failed(FetchProblem.NoAnswer)
        }
        val meta = try {
            val (meta, text) = readArchive(bytes)
            // What the reader will do on opening it, done now: a book that cannot be read is not put on the shelf.
            if (parseBook(meta.id, text).paragraphs.isEmpty()) return Fetching.Failed(FetchProblem.NotABook)
            meta
        } catch (e: BadArchive) {
            runtime.logger.warn("Not a book: $url: ${e.message}")
            return Fetching.Failed(e.problem.asFetchProblem())
        } catch (e: Exception) {
            runtime.logger.warn("Not a book: $url: ${e.message}")
            return Fetching.Failed(FetchProblem.NotABook)
        }
        val name = meta.id + ARCHIVE_SUFFIX
        val before = library.inFolder().filter { it.slug == meta.id }
        val same = before.firstOrNull()
        if (same != null && meta.sha256.isNotEmpty() && same.sha256 == meta.sha256) {
            return Fetching.Done(same, Outcome.Same)
        }
        try {
            folder.write(name, bytes)
            // The person may have kept it under another name: one book, one file, the one just written.
            before.mapNotNull { it.file }.filter { it != name }.forEach { folder.delete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.logger.error("Cannot save $name in ${folder.location}: ${e.message}")
            return Fetching.Failed(FetchProblem.CannotSave)
        }
        val book = loadShelf().find { it.slug == meta.id } ?: meta.toInfo(name)
        return Fetching.Done(book, if (same != null) Outcome.Updated else Outcome.Added)
    }

    private fun resultName(result: Fetching): String = when (result) {
        is Fetching.Done -> result.outcome.name.lowercase()
        is Fetching.Failed -> result.problem.name.lowercase()
        else -> "none"
    }

    /** From the download screen straight into the book it brought. */
    fun readFetched(slug: String) {
        closeFetch()
        open(slug)
    }

    /** Takes a downloaded book off the shelf and out of the folder; its place stays, should it come back. */
    fun remove(slug: String) {
        val book = (_shelf.value as? Shelf.Ready)?.books?.find { it.slug == slug } ?: return
        val file = book.file ?: return
        runtime.scope.launch {
            try {
                folder?.delete(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runtime.logger.error("Cannot delete $file: ${e.message}")
            }
            loadShelf()
        }
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

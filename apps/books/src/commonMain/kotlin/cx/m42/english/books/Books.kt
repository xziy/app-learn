package cx.m42.english.books

import cx.m42.english.books.resources.Res
import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** The lines a sentence has, in the order they are read. */
internal enum class Line(val key: String) {
    /** The ordinary Russian: how we think. */
    Ours("ours"),

    /** The Russian built the English way, word for word: how the English think. */
    Thought("thought"),

    /** How it is said in English. */
    English("english"),

    /** The English in Russian letters, `**` around the stressed vowel. */
    Transcription("transcription"),
}

/**
 * A noun of a sentence, one per time it stands in the English: its dictionary form, what it means here, its sense in
 * the dictionary of the lexicon skill (null for the main one), and how it is written in each line. The thought and the
 * Russian may have no word for it, and then there is nothing to set off there.
 */
internal data class Noun(
    val word: String,
    val meaning: String,
    val form: String,
    val thought: String? = null,
    val ours: String? = null,
    val sense: String? = null,
) {
    /** What it is known by, as in `apps/learn`: `n:sheet`, `n:sheet#paper`. */
    val key: String get() = if (sense == null) "n:$word" else "n:$word#$sense"

    /** How it is written in [line], if it is there at all. */
    fun spelling(line: Line): String? = when (line) {
        Line.English -> form
        Line.Thought -> thought
        Line.Ours -> ours
        Line.Transcription -> null
    }?.trim()?.ifEmpty { null }
}

internal data class Sentence(
    val ours: String,
    val thought: String = "",
    val english: String = "",
    val transcription: String = "",
    val nouns: List<Noun> = emptyList(),
) {
    /** One not put the English way yet has only its Russian. */
    val translated: Boolean get() = english.isNotBlank() || thought.isNotBlank()

    fun text(line: Line): String = when (line) {
        Line.Ours -> ours
        Line.Thought -> thought
        Line.English -> english
        Line.Transcription -> transcription
    }
}

/** A paragraph of the source. A heading is a chapter's title: it is one in the contents too. */
internal data class Paragraph(val n: Int, val sentences: List<Sentence>, val heading: Boolean = false)

/** One way to write and say a form: `names`, `[нэймз]`. */
internal data class WordForm(val word: String, val transcription: String)

/** A sense of a word in the dictionary: null id for the main one. */
internal data class WordSense(val id: String?, val ru: List<String>, val en: String? = null)

/** What the dictionary of the lexicon skill knows about a noun of the book, as `render_text.py` put it in. */
internal data class Word(
    val word: String,
    val transcription: String? = null,
    /** `yes`, `no` or `both`. */
    val countable: String? = null,
    /** `plural_only`, `same` or `collective`. */
    val number: String? = null,
    val plural: List<WordForm> = emptyList(),
    val senses: List<WordSense> = emptyList(),
    val notes: String = "",
)

internal data class Book(
    val slug: String,
    val title: String,
    val author: String = "",
    /** False: the book was put the English way without one, and there is nothing to show in that line. */
    val transcription: Boolean = true,
    val paragraphs: List<Paragraph>,
    val words: Map<String, Word> = emptyMap(),
) {
    val sentences: Int get() = paragraphs.sumOf { it.sentences.size }

    val translated: Int get() = paragraphs.sumOf { p -> p.sentences.count { it.translated } }

    /** How many times each noun stands in the book, by its key. */
    val counts: Map<String, Int> by lazy {
        paragraphs.flatMap { p -> p.sentences.flatMap { s -> s.nouns.map { it.key } } }.groupingBy { it }.eachCount()
    }

    /** The paragraphs that are chapters' titles, by their place in the book. */
    val headings: List<Int> by lazy { paragraphs.indices.filter { paragraphs[it].heading } }
}

/** What the shelf shows of a book without reading it: a row of `files/books/index.json`, or an archive's `meta.json`. */
internal data class BookInfo(
    val slug: String,
    val title: String,
    val author: String = "",
    val paragraphs: Int = 0,
    val sentences: Int = 0,
    val translated: Int = 0,
    /** The archive in the app's folder this book is read from; null for one the build carries. */
    val file: String? = null,
    /** Of the archive's `book.json`: tells a new version from the one on the shelf. */
    val sha256: String = "",
)

internal fun ArchiveMeta.toInfo(file: String): BookInfo =
    BookInfo(id, title, author, paragraphs, sentences, translated, file = file, sha256 = sha256)

/** Where the books come from. */
internal interface BookSource {
    /** Every book there is, as `:apps:books:syncBooks` lists them. */
    suspend fun index(): List<BookInfo>

    /** The JSON of a book. */
    suspend fun read(slug: String): String
}

/**
 * The shelf: the books the build carries and the archives in the app's folder — downloaded, or put there by the
 * person. One in the folder stands in for a carried one with the same id: it is the one they chose, and most likely
 * newer. A file that is not a book is passed over, and the rest of the folder still shows.
 */
internal class Library(
    private val carried: BookSource,
    private val folder: AppFolder?,
    private val logger: Logger,
) : BookSource {
    /** Which archive each book of the folder is in, as the last [index] found them. */
    private var files: Map<String, String> = emptyMap()

    override suspend fun index(): List<BookInfo> {
        val built = try {
            carried.index()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A build from a clean checkout carries no books (they stay out of git): its shelf is the folder alone.
            logger.error("Cannot read the shelf: ${e.message}")
            emptyList()
        }
        val found = inFolder()
        files = found.associate { it.slug to it.file!! }
        return built.filter { it.slug !in files } + found
    }

    /** The books in the folder, by file name; the first file wins when two hold the same book. */
    suspend fun inFolder(): List<BookInfo> {
        val folder = folder ?: return emptyList()
        val names = try {
            folder.list().map { it.name }.filter { it.endsWith(ARCHIVE_SUFFIX) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Cannot read the folder ${folder.location}: ${e.message}")
            return emptyList()
        }
        return names.mapNotNull { name ->
            try {
                folder.read(name)?.let { readArchiveMeta(it).toInfo(name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Not a book, left off the shelf: $name: ${e.message}")
                null
            }
        }.distinctBy { it.slug }
    }

    override suspend fun read(slug: String): String {
        val name = files[slug] ?: return carried.read(slug)
        val bytes = folder?.read(name) ?: error("$name is no longer in the folder")
        return readArchive(bytes).second
    }
}

/** The books as the app carries them: `composeResources/files/books`, filled by `:apps:books:syncBooks`. */
internal object ResourceBookSource : BookSource {
    @OptIn(ExperimentalResourceApi::class)
    override suspend fun index(): List<BookInfo> = parseIndex(Res.readBytes("files/books/index.json").decodeToString())

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun read(slug: String): String = Res.readBytes("files/books/$slug.json").decodeToString()
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

private fun JsonElement?.texts(): List<String> = list().mapNotNull { it.text() }

/** Reads `files/books/index.json`. A row that cannot be read is left out, not the shelf. */
internal fun parseIndex(text: String): List<BookInfo> = Json.parseToJsonElement(text).list().mapNotNull { item ->
    val obj = item as? JsonObject ?: return@mapNotNull null
    BookInfo(
        slug = obj["slug"].text() ?: return@mapNotNull null,
        title = obj["title"].text().orEmpty(),
        author = obj["author"].text().orEmpty(),
        paragraphs = obj["paragraphs"].int() ?: 0,
        sentences = obj["sentences"].int() ?: 0,
        translated = obj["translated"].int() ?: 0,
    )
}

/** Reads a book in the format of the dragunkin-text skill's SKILL.md, «Книга для читалки». */
internal fun parseBook(slug: String, text: String): Book {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: error("A book is one JSON object")
    val paragraphs = root["paragraphs"].list().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val sentences = obj["sentences"].list().mapNotNull { (it as? JsonObject)?.let(::parseSentence) }
        if (sentences.isEmpty()) null else Paragraph(
            n = obj["n"].int() ?: 0,
            sentences = sentences,
            heading = (obj["heading"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
    val words = (root["words"] as? JsonObject).orEmpty().mapNotNull { (word, value) ->
        (value as? JsonObject)?.let { word to parseWord(word, it) }
    }.toMap()
    return Book(
        slug = slug,
        title = root["title"].text() ?: slug,
        author = root["author"].text().orEmpty(),
        transcription = (root["transcription"] as? JsonPrimitive)?.booleanOrNull != false,
        paragraphs = paragraphs,
        words = words,
    )
}

private fun parseSentence(obj: JsonObject): Sentence? {
    val ours = obj["ours"].text() ?: return null
    return Sentence(
        ours = ours,
        thought = obj["thought"].text().orEmpty(),
        english = obj["english"].text().orEmpty(),
        transcription = obj["transcription"].text().orEmpty(),
        nouns = obj["nouns"].list().mapNotNull { item ->
            val noun = item as? JsonObject ?: return@mapNotNull null
            Noun(
                word = noun["word"].text() ?: return@mapNotNull null,
                meaning = noun["meaning"].text().orEmpty(),
                form = noun["form"].text() ?: return@mapNotNull null,
                thought = noun["thought"].text(),
                ours = noun["ours"].text(),
                sense = noun["sense"].text(),
            )
        },
    )
}

private fun parseWord(word: String, obj: JsonObject): Word = Word(
    word = word,
    transcription = obj["transcription"].text(),
    countable = obj["countable"].text(),
    number = obj["number"].text(),
    plural = obj["plural"].list().mapNotNull { item ->
        val form = item as? JsonObject ?: return@mapNotNull null
        WordForm(form["word"].text() ?: return@mapNotNull null, form["transcription"].text().orEmpty())
    },
    senses = obj["senses"].list().mapNotNull { item ->
        val sense = item as? JsonObject ?: return@mapNotNull null
        WordSense(sense["id"].text(), sense["ru"].texts(), sense["en"].text())
    },
    notes = obj["notes"].text().orEmpty(),
)

package cx.m42.english.learn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What the marks of a word in one of its senses are kept under: `n:sheet` for the main sense, `n:sheet#paper` for
 * another. The lexicon skill never renames a sense, so a mark stays on its sense.
 */
internal fun wordKey(word: String, sense: String?): String = if (sense == null) "n:$word" else "n:$word#$sense"

/** A word that can stand for this one in a sense, and how it differs: `page`, «страница книги, а не любой лист». */
internal data class Related(val word: String, val note: String? = null)

/** A word of the same root in another class: `anchor`, a verb, «вставать на якорь». */
internal data class Kin(val word: String, val kind: String, val ru: String)

internal data class Sense(
    /** Null for the main sense. */
    val id: String?,
    /** Its translations, the first being the main one. */
    val ru: List<String>,
    /** Where it is the usual sense, if it belongs to a theme: `sheet` as шкот in `yachting`. */
    val themes: List<String> = emptyList(),
    /** What it is, in English. */
    val en: String? = null,
    val synonyms: List<Related> = emptyList(),
    val antonyms: List<Related> = emptyList(),
    val collocations: List<String> = emptyList(),
)

internal enum class Countable { Yes, No, Both }

/** What is particular about the number of a noun. */
internal enum class NounNumber { PluralOnly, Same, Collective }

/** A word of the dictionary, as the lexicon skill writes it and `syncLexicon` copies it. */
internal data class Entry(
    val word: String,
    val senses: List<Sense>,
    /** False for a word the skill has only begun: it has its translations and no more. */
    val filled: Boolean = false,
    val transcription: String? = null,
    val countable: Countable? = null,
    val plural: List<WordForm> = emptyList(),
    val number: NounNumber? = null,
    val family: List<Kin> = emptyList(),
    val notes: String = "",
    /** How the word is written in the phrases: `children`, `Children`. */
    val forms: List<String> = emptyList(),
    /** The verbs it stands in, by theme. */
    val places: Map<String, List<String>> = emptyMap(),
    /** How many phrases each sense has, by its id; the main sense under null. */
    val counts: Map<String?, Int> = emptyMap(),
) {
    fun key(sense: Sense): String = wordKey(word, sense.id)

    fun sense(id: String?): Sense? = senses.find { it.id == id }

    /** Every way it is written, in lower case: itself, its plural and the forms of the phrases. */
    val spellings: Set<String> by lazy { (listOf(word) + plural.map { it.word } + forms).map { it.lowercase() }.toSet() }

    val phrases: Int get() = counts.values.sum()
}

/** One sense of one word: a row of the dictionary, and what a mark is put on. */
internal data class WordSense(val entry: Entry, val sense: Sense) {
    val key: String get() = entry.key(sense)
}

internal class Lexicon(val entries: List<Entry>) {
    private val byWord = entries.associateBy { it.word }

    fun entry(word: String): Entry? = byWord[word]

    /** The sense a key is of, while the dictionary still has it. */
    fun find(key: String): WordSense? {
        val body = key.removePrefix("n:")
        val word = body.substringBefore('#')
        val sense = body.substringAfter('#', missingDelimiterValue = "").ifEmpty { null }
        val entry = entry(word) ?: return null
        return entry.sense(sense)?.let { WordSense(entry, it) }
    }

    /** Every sense of every word, by the alphabet. */
    val all: List<WordSense> by lazy {
        entries.sortedBy { it.word.lowercase() }.flatMap { entry -> entry.senses.map { WordSense(entry, it) } }
    }

    /** The spellings of a word, or the word alone if the dictionary does not have it. */
    fun spellings(word: String): Set<String> = entry(word)?.spellings ?: setOf(word.lowercase())

    companion object {
        val Empty = Lexicon(emptyList())
    }
}

/** For comparing what is typed with what is written: neither case nor «ё» matter. */
internal fun fold(text: String): String = text.trim().lowercase().replace('ё', 'е')

/**
 * Of [among], what [query] finds, the closest first: the word or one of its forms as typed, then a word, a form or a
 * part of a compound that begins with it, then a translation that does, then anything that has it inside. [among]
 * keeps its order within each of these, so the latest seen stays on top.
 */
internal fun search(query: String, among: List<WordSense>): List<WordSense> {
    val typed = fold(query)
    if (typed.isEmpty()) return among
    fun rank(row: WordSense): Int? {
        val spelled = row.entry.spellings.map(::fold)
        val parts = spelled.flatMap { it.split(' ', '-') }
        val ru = row.sense.ru.map(::fold)
        val ruWords = ru.flatMap { it.split(' ', '-') }
        return when {
            typed in spelled -> 0
            parts.any { it.startsWith(typed) } || spelled.any { it.startsWith(typed) } -> 1
            ru.any { it == typed } || ruWords.any { it.startsWith(typed) } -> 2
            spelled.any { typed in it } || ru.any { typed in it } -> 3
            else -> null
        }
    }
    return among.mapNotNull { row -> rank(row)?.let { it to row } }.sortedBy { it.first }.map { it.second }
}

/** A noun of a phrase where it stands in its text: from [start] to before [end]. */
internal data class Mark(val start: Int, val end: Int, val use: NounUse)

/**
 * Where the nouns of a phrase stand in it: each way they are written, as whole words, in any case. A phrase has one
 * link per noun, yet `the new sailor … the other sailors` has the word twice, so its other spellings are looked for
 * too. Where two overlap the longer wins: `life jacket` over `jacket`.
 */
internal fun nounMarks(text: String, uses: List<NounUse>, spellings: (String) -> Set<String>): List<Mark> {
    val found = uses.flatMap { use ->
        (spellings(use.word) + use.form.lowercase()).flatMap { spelling ->
            Regex("""(?<![\p{L}'’-])${Regex.escape(spelling)}(?![\p{L}-])""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .map { Mark(it.range.first, it.range.last + 1, use) }
        }
    }
    val kept = mutableListOf<Mark>()
    for (mark in found.sortedWith(compareByDescending<Mark> { it.end - it.start }.thenBy { it.start })) {
        if (kept.none { it.start < mark.end && mark.start < it.end }) kept += mark
    }
    return kept.sortedBy { it.start }
}

private val EnglishWord = Regex("""[a-z]+(?:['’-][a-z]+)*""")

/**
 * The same nouns in the transcription: by the words they cover, as the transcription has one word in brackets for
 * each word of the phrase. Returns, for each mark, the first and the last of those words, counted from 0. A
 * transcription with another number of words, which the skill's check does not let through, has none.
 */
internal fun soundMarks(english: String, transcription: String, marks: List<Mark>): List<Pair<IntRange, NounUse>> {
    val words = EnglishWord.findAll(english.lowercase()).map { it.range }.toList()
    val sounds = transcription.trim().removePrefix("[").removeSuffix("]").split(' ').filter { it.isNotEmpty() }
    if (words.size != sounds.size) return emptyList()
    return marks.mapNotNull { mark ->
        val covered = words.indices.filter { words[it].first < mark.end && mark.start <= words[it].last }
        if (covered.isEmpty()) null else covered.first()..covered.last() to mark.use
    }
}

/** Reads `files/lexicon/nouns.json`. A word that cannot be read is left out, not the dictionary. */
internal fun parseLexicon(text: String): Lexicon {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return Lexicon.Empty
    val words = (root["words"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseEntry) }
    return Lexicon(words)
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

private fun JsonElement?.texts(): List<String> = list().mapNotNull { it.text() }

private fun related(element: JsonElement?): List<Related> = element.list().mapNotNull { item ->
    val obj = item as? JsonObject ?: return@mapNotNull null
    obj["word"].text()?.let { Related(it, obj["note"].text()) }
}

private fun parseEntry(obj: JsonObject): Entry? {
    val word = obj["word"].text() ?: return null
    val senses = obj["senses"].list().mapNotNull { item ->
        val sense = item as? JsonObject ?: return@mapNotNull null
        Sense(
            id = sense["id"].text(),
            ru = sense["ru"].texts(),
            themes = sense["themes"].texts(),
            en = sense["en"].text(),
            synonyms = related(sense["synonyms"]),
            antonyms = related(sense["antonyms"]),
            collocations = sense["collocations"].texts(),
        )
    }
    if (senses.isEmpty()) return null
    val noun = obj["noun"] as? JsonObject
    return Entry(
        word = word,
        senses = senses,
        filled = obj["status"].text() == "filled",
        transcription = obj["transcription"].text(),
        countable = when (noun?.get("countable").text()) {
            "yes" -> Countable.Yes
            "no" -> Countable.No
            "both" -> Countable.Both
            else -> null
        },
        plural = noun?.get("plural").list().mapNotNull { item ->
            val form = item as? JsonObject ?: return@mapNotNull null
            val spelled = form["word"].text() ?: return@mapNotNull null
            WordForm(spelled, form["transcription"].text().orEmpty())
        },
        number = when (noun?.get("number").text()) {
            "plural_only" -> NounNumber.PluralOnly
            "same" -> NounNumber.Same
            "collective" -> NounNumber.Collective
            else -> null
        },
        family = obj["family"].list().mapNotNull { item ->
            val kin = item as? JsonObject ?: return@mapNotNull null
            Kin(kin["word"].text() ?: return@mapNotNull null, kin["class"].text().orEmpty(), kin["ru"].text().orEmpty())
        },
        notes = obj["notes"].text().orEmpty(),
        forms = obj["forms"].texts(),
        places = (obj["in"] as? JsonObject).orEmpty().mapValues { (_, verbs) -> verbs.texts() },
        counts = (obj["count"] as? JsonObject).orEmpty().mapNotNull { (sense, count) ->
            (count as? JsonPrimitive)?.intOrNull?.let { sense.ifEmpty { null } to it }
        }.toMap(),
    )
}

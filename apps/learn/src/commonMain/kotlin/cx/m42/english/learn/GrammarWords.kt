package cx.m42.english.learn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * The words of a phrase besides its nouns that a tap opens (D25): an irregular verb, with a star over it, and the
 * service words, the auxiliary and modal verbs among them, dotted under. What they are comes from two tables of the
 * lexicon skill, `files/lexicon/irregular.json` and `files/lexicon/function.json`; without them only the nouns are links.
 */

/** A word of a phrase that opens a page: what its marks are kept under, and the word that page is of. */
internal sealed interface WordLink {
    val key: String
    val word: String
}

/** The key of what is marked on an irregular verb: `v:go`, whichever form was met. */
internal fun verbKey(verb: String): String = "v:$verb"

/** The key of a service word, as it is written: `f:the`, `f:didn't`. */
internal fun serviceKey(word: String): String = "f:$word"

/** A form of an irregular verb as it stands in a phrase: `go`, `went`. */
internal data class VerbLink(override val word: String, val form: String) : WordLink {
    override val key: String get() = verbKey(word)
}

/**
 * A service word as it stands in a phrase: [word] as the table has it, and whether it is part of the formula of the
 * phrase's tense there, as `did` and `not` are in `Did they not accept`.
 */
internal data class ServiceLink(override val word: String, val form: String, val inFormula: Boolean) : WordLink {
    override val key: String get() = serviceKey(word)
}

/** What a service word is. Only the first two are verbs. */
internal enum class WordClass(val key: String) {
    Auxiliary("auxiliary"),
    Modal("modal"),
    Article("article"),
    Determiner("determiner"),
    Pronoun("pronoun"),
    Possessive("possessive"),
    Reflexive("reflexive"),
    Preposition("preposition"),
    Conjunction("conjunction"),
    Question("question"),
    Particle("particle"),
    ;

    val isVerb: Boolean get() = this == Auxiliary || this == Modal

    companion object {
        fun of(key: String?): WordClass? = entries.find { it.key == key }
    }
}

/** Another way to say a word, and when: `[ðи]`, «перед гласным звуком». */
internal data class SoundVariant(val transcription: String, val note: String)

/** A word of `function.json`. */
internal data class ServiceWord(
    val word: String,
    val wordClass: WordClass,
    /** Other classes it often has: `that` is also a conjunction. */
    val also: List<WordClass> = emptyList(),
    /** `be` for `was`, `do` for `didn't`. */
    val lemma: String? = null,
    /** What a short form stands for: `did`, `not`. */
    val parts: List<String> = emptyList(),
    val transcription: String? = null,
    val variants: List<SoundVariant> = emptyList(),
    val ru: String = "",
    /** How the thought line renders it: `[известный]`. Empty where it is not said. */
    val thought: String = "",
    val about: String = "",
) {
    val key: String get() = serviceKey(word)
}

/** A verb of `irregular.json`, with its forms in the order the verb's own card has them. */
internal data class IrregularVerb(val verb: String, val ru: String, val forms: Map<FormKind, List<WordForm>>) {
    val key: String get() = verbKey(verb)

    val transcription: String? get() = forms[FormKind.Base]?.firstOrNull()?.transcription

    /** Every way it is written, in lower case. */
    val spellings: Set<String> by lazy { (forms.values.flatten().map { it.word.lowercase() } + verb.lowercase()).toSet() }

    /** `go — went — gone`, the variants through a slash. */
    val threeForms: String
        get() = listOf(FormKind.Base, FormKind.Past, FormKind.Participle)
            .map { kind -> forms[kind].orEmpty().joinToString("/") { it.word }.ifEmpty { if (kind == FormKind.Base) verb else "—" } }
            .joinToString(" — ")

    /** Which of its forms [written] is: the first that has it, the base before the others. */
    fun formOf(written: String): FormKind? = FormKind.entries.firstOrNull { kind -> forms[kind].orEmpty().any { it.word.equals(written, ignoreCase = true) } }
}

/** Both tables. Either can be missing: then its words are no links, and the rest still are. */
internal class GrammarWords(val service: List<ServiceWord>, val irregular: List<IrregularVerb>) {
    private val serviceByWord = service.associateBy { it.word.lowercase() }
    private val verbs = irregular.associateBy { it.verb }

    /** A form shared by two verbs goes to the one it is the base of: `lay` is lay, not lie's past. */
    private val byForm: Map<String, IrregularVerb> = buildMap {
        irregular.forEach { verb -> put(verb.verb.lowercase(), verb) }
        irregular.forEach { verb -> verb.spellings.forEach { if (it !in this) put(it, verb) } }
    }

    fun service(word: String): ServiceWord? = serviceByWord[word.lowercase()]

    fun verb(word: String): IrregularVerb? = verbs[word]

    /** The irregular verb [written] is a form of. */
    fun verbOf(written: String): IrregularVerb? = byForm[written.lowercase()]

    /** The service words in the order the dictionary lists them: by class, then as the table has them. */
    val serviceSorted: List<ServiceWord> by lazy { service.sortedBy { it.wordClass.ordinal } }

    val irregularSorted: List<IrregularVerb> by lazy { irregular.sortedBy { it.verb } }

    companion object {
        val Empty = GrammarWords(emptyList(), emptyList())
    }
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

/** Reads `function.json`. A word that cannot be read, or of a class this app does not know, is left out. */
internal fun parseServiceWords(text: String): List<ServiceWord> {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
    return root["words"].list().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val word = obj["word"].text()?.lowercase() ?: return@mapNotNull null
        val wordClass = WordClass.of(obj["class"].text()) ?: return@mapNotNull null
        ServiceWord(
            word = word,
            wordClass = wordClass,
            also = obj["also"].list().mapNotNull { WordClass.of(it.text()) },
            lemma = obj["lemma"].text(),
            parts = obj["parts"].list().mapNotNull { it.text() },
            transcription = obj["transcription"].text(),
            variants = obj["variants"].list().mapNotNull { variant ->
                val v = variant as? JsonObject ?: return@mapNotNull null
                SoundVariant(v["transcription"].text() ?: return@mapNotNull null, v["note"].text().orEmpty())
            },
            ru = obj["ru"].text().orEmpty(),
            thought = obj["thought"].text().orEmpty(),
            about = obj["about"].text().orEmpty(),
        )
    }
}

/** Reads `irregular.json`. A verb without its own spelling is left out; a form that cannot be read, from its verb. */
internal fun parseIrregularVerbs(text: String): List<IrregularVerb> {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
    return root["verbs"].list().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val verb = obj["verb"].text() ?: return@mapNotNull null
        val forms = obj["forms"] as? JsonObject
        IrregularVerb(
            verb = verb,
            ru = obj["ru"].text().orEmpty(),
            forms = FormKind.entries.associateWith { kind ->
                forms?.get(kind.key).list().mapNotNull { element ->
                    val form = element as? JsonObject ?: return@mapNotNull null
                    WordForm(form["word"].text() ?: return@mapNotNull null, form["transcription"].text().orEmpty())
                }
            }.filterValues { it.isNotEmpty() },
        )
    }
}

/** Words after which a form of a verb is a noun or an adjective instead: `the left side`, `a light breeze`. */
private val Before = setOf(
    WordClass.Article, WordClass.Determiner, WordClass.Possessive,
)

/**
 * Words after which the base form of a verb cannot be a verb: `be` takes a participle or an `-ing`, so `is light` and
 * `stays light` are adjectives. The same list is in the lexicon skill's `grammar_words.py`.
 */
private val Linking = setOf(
    "am", "is", "are", "was", "were", "be", "been", "being",
    "stay", "stays", "stayed", "remain", "remains", "remained", "seem", "seems", "seemed",
)

/**
 * Whether a form of a verb that is not the phrase's own stands as something else there: right after an article or the
 * like (`the left side`), as a base or `-ing` form straight before a noun of the phrase (`light winds`, `falling
 * pressure`), as a base form after `be` (`the wind is light`), or with a capital inside the phrase, a name (`in Split`).
 */
private fun notAVerb(text: String, spot: Spot, previous: Spot?, next: Spot?, verb: IrregularVerb, nouns: List<Mark>, grammar: GrammarWords): Boolean {
    if (previous?.let { grammar.service(it.word)?.wordClass } in Before) return true
    if (previous != null && text[spot.start].isUpperCase()) return true
    val kind = verb.formOf(spot.word)
    if (kind == FormKind.Base || kind == FormKind.Ing) {
        if (next != null && nouns.any { it.start == next.start }) return true
    }
    return kind == FormKind.Base && previous?.word in Linking
}

/** A word of a phrase and where it stands in it: from [start] to before [end]. */
private data class Spot(val start: Int, val end: Int, val word: String, val role: Role?)

/**
 * The words of [text], in the pieces [breakdown] has, with where each stands. The pieces are looked for in the text
 * itself, so a second space between two words does not move the rest.
 */
private fun spots(text: String, breakdown: Breakdown): List<Spot> = buildList {
    var at = 0
    for (piece in breakdown.pieces) {
        val shown = piece.text.trim()
        if (shown.isEmpty()) continue
        val found = text.indexOf(shown, at)
        if (found < 0) break
        at = found + shown.length
        // The word without what is around it: quotes, a comma, the question mark.
        var start = found
        var end = at
        while (start < end && !text[start].isLetterOrDigit() && text[start] != '\'' && text[start] != '’') start++
        while (end > start && !text[end - 1].isLetterOrDigit()) end--
        if (start >= end) continue
        val word = text.substring(start, end).replace('’', '\'').lowercase()
        add(Spot(start, end, word, piece.role))
    }
}

/**
 * Every link of a phrase of [verb] in [tense]: its [nouns] as they are, then, on the words they leave, the verb of the
 * phrase if it is irregular, its auxiliaries, the service words, and the forms of other irregular verbs, `said` in
 * `He said that…`. Such a form where it is a noun, an adjective or a name is left alone, see [notAVerb]. Without either
 * table there is nothing more than the nouns.
 */
internal fun phraseMarks(text: String, verb: Verb, tense: Tense, nouns: List<Mark>, grammar: GrammarWords): List<Mark> {
    if (grammar.service.isEmpty() && grammar.irregular.isEmpty()) return nouns
    val breakdown = parsePhrase(text, verb, tense)
    val found = spots(text, breakdown)
    val own = grammar.verb(verb.word)
    val marks = nouns.toMutableList()
    fun free(spot: Spot) = nouns.none { it.start < spot.end && spot.start < it.end }
    found.forEachIndexed { index, spot ->
        if (!free(spot)) return@forEachIndexed
        val service = grammar.service(spot.word)
        val previous = found.getOrNull(index - 1)
        val next = found.getOrNull(index + 1)
        val link: WordLink? = when {
            spot.role == Role.Verb -> own?.let { VerbLink(it.verb, spot.word) }
            spot.role == Role.Auxiliary || (spot.role == Role.Not && service != null) ->
                ServiceLink(service?.word ?: spot.word, spot.word, inFormula = true)
            service != null -> ServiceLink(service.word, spot.word, inFormula = false)
            else -> {
                val other = if (own != null && spot.word in own.spellings) own else grammar.verbOf(spot.word)
                other?.takeIf { !notAVerb(text, spot, previous, next, it, nouns, grammar) }?.let { VerbLink(it.verb, spot.word) }
            }
        }
        if (link != null) marks += Mark(spot.start, spot.end, link)
    }
    return marks.sortedBy { it.start }
}

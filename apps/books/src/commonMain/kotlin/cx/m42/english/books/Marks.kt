package cx.m42.english.books

/** A noun set off in a line: from [start] to before [end], the [noun]-th of its sentence. */
internal data class Span(val start: Int, val end: Int, val noun: Int)

/** A word in any case, standing alone: `car` in `my car's door`, not in `cartoon`; `имя` in «Имя», not in «имяреку». */
private fun wholeWord(spelling: String): Regex =
    Regex("""(?<![\p{L}\p{N}])${Regex.escape(spelling.lowercase())}(?![\p{L}\p{N}])""")

/**
 * Where the nouns of a sentence stand in one of its lines. Each noun is written there as [Noun.spelling] says, and the
 * same spelling twice is the first time and the second: `the man … the other man` has two nouns and two places. The
 * skill's check makes sure each is there; one that is not, after a hand edit, is simply not set off. Where two would
 * overlap the first one stays.
 */
internal fun spans(text: String, nouns: List<Noun>, line: Line): List<Span> {
    if (text.isEmpty()) return emptyList()
    // Lower case keeps the length of every letter these texts have, so its places are the text's places.
    val folded = text.lowercase().takeIf { it.length == text.length } ?: return emptyList()
    val taken = mutableMapOf<String, Int>()
    val found = mutableListOf<Span>()
    nouns.forEachIndexed { index, noun ->
        val spelling = noun.spelling(line) ?: return@forEachIndexed
        val key = spelling.lowercase()
        val nth = taken[key] ?: 0
        taken[key] = nth + 1
        val match = wholeWord(spelling).findAll(folded).elementAtOrNull(nth) ?: return@forEachIndexed
        val span = Span(match.range.first, match.range.last + 1, index)
        if (found.none { it.start < span.end && span.start < it.end }) found += span
    }
    return found.sortedBy { it.start }
}

private val EnglishWord = Regex("""[a-z]+(?:['’-][a-z]+)*""")

/** The words of a transcription: `[ай ноу май нэйм]` is four, as its English is. */
internal fun soundWords(transcription: String): List<String> =
    transcription.trim().removePrefix("[").removeSuffix("]").split(' ').filter { it.isNotEmpty() }

/**
 * The same nouns in the transcription, which has one word for each word of the English: for each word of it, the noun
 * it says, if any. A transcription with another number of words, as with digits in the English, has none.
 */
internal fun soundNouns(english: String, transcription: String, englishSpans: List<Span>): Map<Int, Int> {
    val words = EnglishWord.findAll(english.lowercase()).map { it.range }.toList()
    val sounds = soundWords(transcription)
    if (words.isEmpty() || words.size != sounds.size) return emptyMap()
    return buildMap {
        englishSpans.forEach { span ->
            words.indices.filter { words[it].first < span.end && span.start <= words[it].last }.forEach { put(it, span.noun) }
        }
    }
}

/** A piece of a transcription: its text without the `**`, and whether it is the stressed vowel. */
internal data class Sound(val text: String, val stressed: Boolean)

private val Stress = Regex("""\*\*(.+?)\*\*""")

/** `м**а**нк` as `м`, stressed `а`, `нк`. */
internal fun sounds(word: String): List<Sound> = buildList {
    var at = 0
    Stress.findAll(word).forEach { match ->
        if (match.range.first > at) add(Sound(word.substring(at, match.range.first), false))
        add(Sound(match.groupValues[1], true))
        at = match.range.last + 1
    }
    if (at < word.length) add(Sound(word.substring(at), false))
}

/**
 * How a noun is said in its sentence, when the dictionary does not say it: its words of the transcription, between
 * brackets, the stress marks kept. Null when the transcription and the English do not line up.
 */
internal fun soundOf(sentence: Sentence, noun: Int): String? {
    val english = spans(sentence.english, sentence.nouns, Line.English)
    val words = soundWords(sentence.transcription)
    val mine = soundNouns(sentence.english, sentence.transcription, english).filterValues { it == noun }.keys.sorted()
    if (mine.isEmpty()) return null
    return mine.joinToString(" ", "[", "]") { words[it] }
}

/** Russian has three forms for a count: 1 фраза, 2 фразы, 5 фраз. */
internal fun ruCount(n: Int, one: String, few: String, many: String): String {
    val tens = n % 100
    val last = n % 10
    val word = when {
        tens in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

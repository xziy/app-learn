package cx.m42.english.learn

/**
 * What the cap's page says, in the app's language: the one chosen in the host's settings, or the device's where none is.
 * The page is for someone who knows no grammar yet, so no term is left unexplained: a form is the 1st, 2nd or 3rd and
 * what that is, an auxiliary is a helper word and what it marks, and a rule shows itself on `wash` or on the phrase. The
 * thought is Russian in either language, and so is what the page quotes of it.
 *
 * Here is what does not depend on the language: how a phrase falls into parts and which signs its formula has. The
 * words are [RussianRules] and [EnglishRules].
 */
internal abstract class RuleTexts {
    abstract val phraseTitle: String

    /** Over the ordinary Russian phrase. */
    abstract val translation: String

    /** What the thought is, under it. */
    abstract val thoughtAbout: String
    abstract val partsTitle: String
    abstract val noBreakdown: String
    abstract val formulaTitle: String
    abstract val inEnglish: String
    abstract val thoughtOnWash: String
    abstract val rulesTitle: String
    abstract val glossaryTitle: String

    /** The words the page uses, in plain words. */
    abstract val glossary: List<Pair<String, String>>

    /** Who does it in a formula, and what it is done to in the passive. */
    protected abstract val who: String
    protected abstract val what: String

    /** `и`, `and`: before the last of a list. */
    protected abstract val and: String

    fun subjectOf(tense: Tense): String = if (tense.isPassive) what else who

    /** The line under the title: what this tense and this kind are for, in plain words, and what its names mean. */
    fun about(tense: Tense, kind: Kind): String =
        listOfNotNull(whenSaid(tense), kindAbout(kind), voiceAbout(tense), names(tense)).joinToString(" ")

    protected abstract fun whenSaid(tense: Tense): String?
    protected abstract fun kindAbout(kind: Kind): String
    protected abstract fun voiceAbout(tense: Tense): String?
    protected abstract fun names(tense: Tense): String?

    /** `Утверждение и отрицание`, out of the names of the kinds the page shows besides its own. */
    fun othersTitle(names: List<String>): String {
        val list = names.mapIndexed { index, name -> if (index == 0) name else name.replaceFirstChar { it.lowercase() } }
        return if (list.size == 1) list.single() else list.dropLast(1).joinToString(", ") + " $and " + list.last()
    }

    /** What to keep in mind: how this kind is made, how the tense is, and what this phrase has besides. */
    abstract fun rules(phrase: Breakdown, tense: Tense, verb: String, english: String): List<String>

    // The parts of a phrase.

    /** The part of the future in the past that says who said or knew it. */
    protected abstract val mainPart: String

    /** Words before the subject: a time or a place. */
    protected abstract val frontPart: String

    /** Whatever follows the verb. */
    protected abstract val restPart: String

    protected abstract fun questionWordPart(several: Boolean, russian: String?): String
    protected abstract fun subjectPart(passive: Boolean, russian: String?): String

    /** What [Aux.word] is: `служебное слово прошедшего времени`. */
    protected abstract fun auxAbout(aux: Aux): String

    /** How the thought says an auxiliary: in square brackets, if Russian has no such word. */
    protected abstract fun inThought(thought: String): String

    /** `«буду»`; what has its own quotes says so. */
    protected abstract fun quoted(thought: String): String

    /** Before what a short form stands for: `это will, `. */
    protected abstract fun writtenOut(full: String): String
    protected abstract val unknownAux: String

    /** Several auxiliaries in a row, each with how the thought says it. */
    protected abstract fun severalAux(each: String): String

    /** `not` on its own. */
    protected abstract val bareNot: String

    /** `didn't`: [full] is `did not`; [about] and [thought] are those of `did`, if it is known. */
    protected abstract fun notWith(full: String, about: String?, thought: String?): String
    protected abstract fun adverbPart(russian: String?): String

    /** The verb: its Russian [meaning], its [form], and why that form, after [reason]'s word. */
    protected abstract fun verbPart(meaning: String, form: FormKind?, reason: Pair<Reason, String>?): String

    /** Why the verb is in its form: what stands before it. */
    protected enum class Reason { BaseAfterHelper, ThirdAfterHave, ThirdAfterBe, IngAfterBe }

    /**
     * The phrase in parts, each with what it is: a run of words of one role, or a run of words with none. A part has
     * letters: punctuation on its own is no part.
     */
    fun parts(phrase: Breakdown, verb: Verb, tense: Tense): List<Part> {
        val runs = buildList<Piece> {
            for (piece in phrase.pieces) {
                val last = lastOrNull()
                if (last != null && last.role == piece.role) set(size - 1, Piece(last.text + piece.text, piece.role)) else add(piece)
            }
        }
        val first = runs.indexOfFirst { it.role != null }
        val subject = runs.indexOfLast { it.role == Role.Subject }
        // The last auxiliary before a part, written out: `did` for `didn't`, `will` for `'ll`.
        var before: String? = null
        return runs.mapIndexedNotNull { index, run ->
            // The phrase's own full stop or question mark is no part of the last words.
            val text = run.text.trim().trimEnd { it in ".,!?;:" }
            if (text.none { it.isLetterOrDigit() }) return@mapIndexedNotNull null
            val words = text.split(' ').filter { it.isNotBlank() }
            val about = when (run.role) {
                null -> when {
                    tense.voice == null && index < subject -> mainPart
                    index < first -> frontPart
                    else -> restPart
                }
                Role.QuestionWord -> questionWordPart(words.size > 1, QuestionWordsRu[plainWord(words.first())])
                Role.Subject -> subjectPart(tense.isPassive, PronounsRu[plainWord(text)])
                Role.Auxiliary -> auxiliaryPart(words, tense, before)
                Role.Not -> notPart(words, tense, before)
                Role.Adverb -> adverbPart(words.mapNotNull { AdverbsRu[plainWord(it)] }.joinToString(", ").ifEmpty { null })
                Role.Verb -> verbPart(verb.meaning.substringBefore(';').trim(), phrase.form, reason(phrase.form, before))
            }
            if (run.role == Role.Auxiliary || run.role == Role.Not) {
                words.flatMap { spelled(it, tense) }.lastOrNull { it != "not" }?.let { before = it }
            }
            Part(text, run.role, about)
        }
    }

    private fun auxiliaryPart(words: List<String>, tense: Tense, before: String?): String {
        if (words.size == 1) {
            val full = spelled(words.single(), tense)
            val aux = aux(full.last(), before, tense) ?: return unknownAux
            val written = full.joinToString(" ")
            val short = if (written != plainWord(words.single())) writtenOut(written) else ""
            return "$short${auxAbout(aux)}. ${inThought(aux.thought)}"
        }
        var previous = before
        val each = words.joinToString(", ") { written ->
            val word = spelled(written, tense).last()
            val thought = aux(word, previous, tense)?.thought
            previous = word
            if (thought == null) written else "$written — ${quoted(thought)}"
        }
        return severalAux(each)
    }

    private fun notPart(words: List<String>, tense: Tense, before: String?): String {
        val full = words.flatMap { spelled(it, tense) }
        val word = full.lastOrNull { it != "not" } ?: return bareNot
        val aux = aux(word, before, tense)
        return notWith(full.joinToString(" "), aux?.let(::auxAbout), aux?.thought)
    }

    private fun reason(form: FormKind?, before: String?): Pair<Reason, String>? {
        if (before == null) return null
        val reason = when {
            form == FormKind.Base && (before in Doers || before in TakeBase) -> Reason.BaseAfterHelper
            form == FormKind.Participle && before in Havers -> Reason.ThirdAfterHave
            form == FormKind.Participle && before in BeWords -> Reason.ThirdAfterBe
            form == FormKind.Ing && before in BeWords -> Reason.IngAfterBe
            else -> return null
        }
        return reason to before
    }

    // The formula.

    protected abstract fun subjectLegend(passive: Boolean): String
    protected abstract fun formName(kind: FormKind): String
    protected abstract val restLegend: String
    protected abstract val bracketsLegend: String
    protected abstract val longLegend: String

    /** What the signs of a formula stand for, with this verb's own forms. */
    fun legend(formula: Formula, verb: Verb, tense: Tense): List<Legend> = buildList {
        if (formula.english.any { it == Slot.Subject }) add(Legend("subject", subjectOf(tense), Role.Subject, subjectLegend(tense.isPassive)))
        formula.english.filterIsInstance<Slot.Form>().flatMap { it.kinds }.distinct().forEach { kind ->
            val words = verb.forms[kind]?.joinToString(" / ") { it.word }
            add(Legend("form-${kind.key}", kind.symbol(), Role.Verb, formName(kind) + (words?.let { ": $it" } ?: "")))
        }
        add(Legend("rest", "…", null, restLegend))
        val thought = formula.thought.filterIsInstance<Slot.Word>().map { it.text }
        if (thought.any { '[' in it }) add(Legend("brackets", "[ ]", null, bracketsLegend))
        if (thought.any { "«долго»" in it }) add(Legend("long", "«долго»", null, longLegend))
    }
}

/** A part of a phrase and what it is. No role: the rest of the phrase. */
internal data class Part(val text: String, val role: Role?, val about: String)

/** A sign of a formula and what it stands for; [key] names it for tests. */
internal data class Legend(val key: String, val sign: String, val role: Role?, val text: String)

/** An auxiliary as the page explains it: the word written out, how the thought says it, and whether it is `have` after `will`. */
internal data class Aux(val word: String, val thought: String, val base: Boolean = false)

/** A passive that is said as one, not the one the course says in the active with `they`. */
internal val Tense.isPassive: Boolean get() = voice == Voice.Passive && !isActiveWithThey

internal fun isBeOfType1(tense: Tense, verb: String) =
    verb == "be" && tense.voice == Voice.Active && tense.aspect == Aspect.Simple && tense.time != Time.Future

internal val PronounsRu = mapOf("i" to "я", "you" to "ты", "he" to "он", "she" to "она", "it" to "оно", "we" to "мы", "they" to "они")

internal val QuestionWordsRu = mapOf(
    "what" to "что", "when" to "когда", "where" to "где", "why" to "почему", "how" to "как", "who" to "кто",
    "whom" to "кого", "whose" to "чей", "which" to "который",
)

internal val AdverbsRu = mapOf(
    "already" to "уже", "never" to "никогда", "just" to "только что", "still" to "всё ещё", "always" to "всегда",
    "ever" to "когда-либо", "often" to "часто", "usually" to "обычно", "also" to "также", "really" to "действительно",
    "finally" to "наконец", "recently" to "недавно", "completely" to "полностью", "sometimes" to "иногда", "soon" to "скоро",
    "seldom" to "редко", "rarely" to "редко", "probably" to "вероятно", "even" to "даже", "only" to "только",
    "hardly" to "едва", "long" to "долго",
)

internal val NegativesRu = mapOf(
    "never" to "никогда", "nothing" to "ничто", "no" to "никакой", "none" to "ни один", "nobody" to "никто",
    "neither" to "ни один из двух", "nowhere" to "нигде",
)

internal val RussianModals = mapOf(
    "can" to "могу", "could" to "мог", "may" to "могу", "might" to "мог бы",
    "must" to "должен", "should" to "следует", "need" to "нужно",
)

private val Doers = setOf("do", "does", "did")

/** After these the verb is in its first form, and `have` is `иметь`, not `имею`. */
private val TakeBase = setOf("will", "would", "shall") + RussianModals.keys

private val Havers = setOf("have", "has", "had")

private val BeWords = setOf("am", "is", "are", "was", "were", "be", "been", "being")

/** A word without its punctuation and its case, `’` as `'`. */
internal fun plainWord(word: String): String = word.replace('’', '\'').lowercase().trim { !it.isLetterOrDigit() && it != '\'' }

/** The adverbs inside the verb's group, each once. */
internal fun Breakdown.adverbs(): List<String> = pieces.filter { it.role == Role.Adverb }.map { plainWord(it.text) }.distinct()

private val Article = Regex("""\b(the|a|an)\b""", RegexOption.IGNORE_CASE)

internal fun hasArticle(english: String): Boolean = Article.containsMatchIn(english)

/** A word as it is when written out: `didn't` is `did not`, `'s` in type 3 is `has`. */
private fun spelled(word: String, tense: Tense): List<String> {
    val written = spelledOut(word) ?: return listOf(plainWord(word))
    val options = written.split(", ")
    val perfect = tense.aspect == Aspect.Perfect || tense.aspect == Aspect.PerfectContinuous
    val pick = options.firstOrNull {
        when (it) {
            "has", "had" -> perfect
            "is" -> !perfect
            "would" -> tense.voice == null
            else -> true
        }
    } ?: options.first()
    return pick.split(' ')
}

/** An auxiliary and how the thought says it; null for a word that is none. */
private fun aux(word: String, before: String?, tense: Tense): Aux? {
    val passive = tense.isPassive
    val thought = when (word) {
        "do" -> "[делаю]"
        "does" -> "[делает]"
        "did" -> "[сделал]"
        "will", "shall" -> "буду"
        "would" -> "будет"
        "am" -> if (passive) "есть" else "являюсь"
        "is" -> if (passive) "есть" else "является"
        "are" -> if (passive) "есть" else "являешься"
        "was" -> "был"
        "were" -> "были"
        "be", "been" -> "быть"
        "being" -> "«долго»"
        "have" -> if (before in TakeBase) return Aux(word, "иметь", base = true) else "имею"
        "has" -> "имеет"
        "had" -> "имел"
        else -> RussianModals[word] ?: return null
    }
    return Aux(word, thought)
}

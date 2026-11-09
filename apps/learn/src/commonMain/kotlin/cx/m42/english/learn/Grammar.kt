package cx.m42.english.learn

/*
 * What a phrase is and how it is built: the kind of sentence, the formula of its tense for that kind, and the phrase
 * itself laid along the formula. The base says none of this, so it is read off the English: the verb's own forms find
 * the verb, and the small words around it are its auxiliaries.
 */

/** The three kinds the course gives each tense, and a question told inside a statement: `He asked whether…`. */
internal enum class Kind { Statement, Negative, Question, Reported }

/** What a word does in a formula. The rest of a phrase, its objects and its times, has none. */
internal enum class Role { QuestionWord, Subject, Auxiliary, Not, Adverb, Verb }

/** A word of a phrase as it is written, with the space before it: the pieces put together give the phrase back. */
internal data class Piece(val text: String, val role: Role? = null)

/** A phrase read along its formula. */
internal data class Breakdown(
    val kind: Kind,
    /** The phrase word by word. Without its verb found in it, no word has a role. */
    val pieces: List<Piece>,
    /** Which of the verb's forms stands in it. */
    val form: FormKind? = null,
    /** `never`, `nothing`, `no`: a negative with no `not`, and the verb as in a statement. */
    val negativeWord: String? = null,
    /** `could`, `can`: a modal verb in the place of the tense's own auxiliary. */
    val modal: String? = null,
) {
    val hasVerb: Boolean get() = pieces.any { it.role == Role.Verb }

    /** Neighbouring words of one role as one piece: `the sailors`, `will have been`. */
    val chunks: List<Piece>
        get() = buildList {
            for (piece in pieces) {
                val last = lastOrNull()
                if (last != null && last.role == piece.role && piece.role != null) {
                    set(size - 1, Piece(last.text + piece.text, last.role))
                } else {
                    add(piece)
                }
            }
        }
}

/** A place in a formula: a word as it is, the subject, or one of the verb's forms. */
internal sealed interface Slot {
    val role: Role?

    data class Word(val text: String, override val role: Role? = Role.Auxiliary) : Slot

    data object Subject : Slot {
        override val role: Role = Role.Subject
    }

    /** One of these forms of the verb: `V`, or `V / V-s` where the person decides. */
    data class Form(val kinds: List<FormKind>) : Slot {
        override val role: Role = Role.Verb
    }
}

/**
 * A formula of a tense for one kind of sentence: in English, and in the order the English think it in Russian. It
 * ends with what the phrase goes on with, so neither has it.
 */
internal data class Formula(val kind: Kind, val english: List<Slot>, val thought: List<Slot>) {
    val question: Boolean get() = kind == Kind.Question
}

/** The verb's form as a formula writes it. */
internal fun FormKind.symbol(): String = when (this) {
    FormKind.Base -> "V"
    FormKind.Past -> "V2"
    FormKind.Participle -> "V3"
    FormKind.Ing -> "V-ing"
    FormKind.S -> "V-s"
}

/** The name the tense has in English grammars: `Past Simple`, `Present Perfect Passive`. */
internal fun Tense.englishName(): String {
    if (voice == null) return "Future in the Past"
    if (aspect == null) return if (voice == Voice.Active) "Active" else "Passive"
    val time = when (time) {
        Time.Past -> "Past "
        Time.Present -> "Present "
        Time.Future -> "Future "
        null -> ""
    }
    val aspect = when (aspect) {
        Aspect.Simple -> "Simple"
        Aspect.Continuous -> "Continuous"
        Aspect.Perfect -> "Perfect"
        Aspect.PerfectContinuous -> "Perfect Continuous"
    }
    // The passive of type 2 in the future is said in the active, with `they`.
    val passive = voice == Voice.Passive && !isActiveWithThey
    return "$time$aspect" + if (passive) " Passive" else ""
}

/** The one tense the course says in the other voice: `will be being washed` turns into `they will be washing`. */
internal val Tense.isActiveWithThey: Boolean
    get() = voice == Voice.Passive && aspect == Aspect.Continuous && time == Time.Future

/** The kinds of sentence a tense is learned in, in the order its phrases go. */
internal val Tense.kinds: List<Kind>
    get() = if (voice == null) listOf(Kind.Statement, Kind.Negative, Kind.Question, Kind.Reported) else listOf(Kind.Statement, Kind.Negative, Kind.Question)

/**
 * How a tense is built, as the course gives it with `wash`: the first auxiliary of a statement and what follows it, the
 * verb's form, the first auxiliary with `not` and in front of a question; then the same in the Russian of the thought.
 * The forms after `do` and in the Russian after `[сделал]` are those of a negative and a question.
 */
private class Build(
    val first: String?,
    val rest: String?,
    val form: List<FormKind>,
    val no: String,
    val ask: String,
    val askForm: List<FormKind> = form,
    val ruFirst: String?,
    val ruRest: String?,
    val ruVerb: String,
    val ruNo: String,
    val ruAsk: String,
    val ruAskVerb: String = ruVerb,
    /** The subject a tense always has: `they` where the passive turns into the active. */
    val subject: Pair<String, String>? = null,
) {
    fun formula(kind: Kind): Formula {
        val who: Slot = subject?.let { Slot.Word(it.first, Role.Subject) } ?: Slot.Subject
        val ruWho: Slot = subject?.let { Slot.Word(it.second, Role.Subject) } ?: Slot.Subject
        fun words(vararg text: String?, role: Role = Role.Auxiliary) = text.filterNotNull().map { Slot.Word(it, role) }
        return when (kind) {
            Kind.Statement, Kind.Reported -> Formula(
                kind,
                listOf(who) + words(first, rest) + Slot.Form(form),
                listOf(ruWho) + words(ruFirst, ruRest) + Slot.Word(ruVerb, Role.Verb),
            )
            Kind.Negative -> Formula(
                kind,
                listOf(who) + words(no, role = Role.Not) + words(rest) + Slot.Form(askForm),
                listOf(ruWho) + words(ruNo, role = Role.Not) + words(ruRest) + Slot.Word(ruAskVerb, Role.Verb),
            )
            Kind.Question -> Formula(
                kind,
                words(ask) + who + words(rest) + Slot.Form(askForm),
                words(ruAsk) + ruWho + words(ruRest) + Slot.Word(ruAskVerb, Role.Verb),
            )
        }
    }
}

private val Base = listOf(FormKind.Base)
private val Ing = listOf(FormKind.Ing)
private val Participle = listOf(FormKind.Participle)

private fun active(aspect: Aspect, time: Time): Build = when (aspect) {
    Aspect.Simple -> when (time) {
        Time.Present -> Build(
            null, null, listOf(FormKind.Base, FormKind.S), "don't/doesn't", "Do/Does", Base,
            null, null, "мою (моет)", "[делаю/делает] не", "[Делаешь/Делает]", "мыть",
        )
        Time.Past -> Build(
            null, null, listOf(FormKind.Past), "didn't", "Did", Base,
            null, null, "вымыл", "[сделал] не", "[Сделал]", "мыть",
        )
        Time.Future -> Build("will", null, Base, "won't", "Will", Base, "буду", null, "мыть", "буду не", "Будешь")
    }
    Aspect.Continuous -> when (time) {
        Time.Present -> Build("am/is/are", null, Ing, "am not/isn't/aren't", "Am/Is/Are", Ing, "являюсь", null, "моющим", "являюсь не", "Являешься")
        Time.Past -> Build("was/were", null, Ing, "wasn't/weren't", "Was/Were", Ing, "был", null, "моющим", "был не", "Был")
        Time.Future -> Build("will", "be", Ing, "won't", "Will", Ing, "буду", "быть", "моющим", "буду не", "Будешь")
    }
    Aspect.Perfect -> when (time) {
        Time.Present -> Build("have/has", null, Participle, "haven't/hasn't", "Have/Has", Participle, "имею", null, "вымытой", "имею не", "Имеешь")
        Time.Past -> Build("had", null, Participle, "hadn't", "Had", Participle, "имел", null, "вымытой", "имел не", "Имел")
        Time.Future -> Build("will", "have", Participle, "won't", "Will", Participle, "буду", "иметь", "вымытой", "буду не", "Будешь")
    }
    Aspect.PerfectContinuous -> when (time) {
        Time.Present -> Build("have/has", "been", Ing, "haven't/hasn't", "Have/Has", Ing, "имею", "быть", "моющим", "имею не", "Имеешь")
        Time.Past -> Build("had", "been", Ing, "hadn't", "Had", Ing, "имел", "быть", "моющим", "имел не", "Имел")
        Time.Future -> Build("will", "have been", Ing, "won't", "Will", Ing, "буду", "иметь быть", "моющим", "буду не", "Будешь")
    }
}

private fun passive(aspect: Aspect, time: Time): Build = when (aspect) {
    Aspect.Simple -> when (time) {
        Time.Present -> Build("am/is/are", null, Participle, "am not/isn't/aren't", "Am/Is/Are", Participle, "есть", null, "вымыта", "есть не", "Есть")
        Time.Past -> Build("was/were", null, Participle, "wasn't/weren't", "Was/Were", Participle, "была", null, "вымыта", "была не", "Была")
        Time.Future -> Build("will", "be", Participle, "won't", "Will", Participle, "будет", "быть", "вымыта", "будет не", "Будет")
    }
    Aspect.Continuous -> when (time) {
        Time.Present -> Build("am/is/are", "being", Participle, "am not/isn't/aren't", "Am/Is/Are", Participle, "есть", "«долго»", "вымыта", "есть не", "Есть")
        Time.Past -> Build("was/were", "being", Participle, "wasn't/weren't", "Was/Were", Participle, "была", "«долго»", "вымыта", "была не", "Была")
        Time.Future -> Build(
            "will", "be", Ing, "won't", "Will", Ing, "будут", "быть", "моющими", "будут не", "Будут",
            subject = "they" to "они",
        )
    }
    Aspect.Perfect -> when (time) {
        Time.Present -> Build("have/has", "been", Participle, "haven't/hasn't", "Have/Has", Participle, "имеет", "быть", "вымыта", "имеет не", "Имеет")
        Time.Past -> Build("had", "been", Participle, "hadn't", "Had", Participle, "имела", "быть", "вымыта", "имела не", "Имела")
        Time.Future -> Build("will", "have been", Participle, "won't", "Will", Participle, "будет", "иметь быть", "вымыта", "будет не", "Будет")
    }
    Aspect.PerfectContinuous -> when (time) {
        Time.Present -> Build("have/has", "been being", Participle, "haven't/hasn't", "Have/Has", Participle, "имеет", "быть «долго»", "вымыта", "имеет не", "Имеет")
        Time.Past -> Build("had", "been being", Participle, "hadn't", "Had", Participle, "имела", "быть «долго»", "вымыта", "имела не", "Имела")
        Time.Future -> Build("will", "have been being", Participle, "won't", "Will", Participle, "будет", "иметь быть «долго»", "вымыта", "будет не", "Будет")
    }
}

/** `be` needs no `do`: its own forms go in front of a question and take `not`. */
private fun be(time: Time, kind: Kind): Formula? {
    val (form, ru) = when (time) {
        Time.Present -> listOf(FormKind.S) to "есть"
        Time.Past -> listOf(FormKind.Past) to "был"
        Time.Future -> return null
    }
    val verb = Slot.Form(form)
    return when (kind) {
        Kind.Statement, Kind.Reported -> Formula(kind, listOf(Slot.Subject, verb), listOf(Slot.Subject, Slot.Word(ru, Role.Verb)))
        Kind.Negative -> Formula(
            kind,
            listOf(Slot.Subject, verb, Slot.Word("not", Role.Not)),
            listOf(Slot.Subject, Slot.Word(ru, Role.Verb), Slot.Word("не", Role.Not)),
        )
        Kind.Question -> Formula(
            kind,
            listOf(verb, Slot.Subject),
            listOf(Slot.Word(ru.replaceFirstChar { it.uppercase() }, Role.Verb), Slot.Subject),
        )
    }
}

/** The future in the past: `will` of the future turned into `would`, after `said`, `knew` or `thought`. */
private fun futureInPast(kind: Kind): Formula {
    val said = Slot.Word("said/knew (that)", null)
    val ruSaid = Slot.Word("сказал (знал), что", null)
    val would = Slot.Word("would")
    val verb = Slot.Form(Base)
    return when (kind) {
        Kind.Statement -> Formula(
            kind,
            listOf(Slot.Subject, said, Slot.Subject, would, verb),
            listOf(Slot.Subject, ruSaid, Slot.Subject, Slot.Word("будет"), Slot.Word("мыть", Role.Verb)),
        )
        Kind.Negative -> Formula(
            kind,
            listOf(Slot.Subject, said, Slot.Subject, Slot.Word("wouldn't", Role.Not), verb),
            listOf(Slot.Subject, ruSaid, Slot.Subject, Slot.Word("будет не", Role.Not), Slot.Word("мыть", Role.Verb)),
        )
        // The question is asked by the verb in front, in its own tense; the future in the past stays as it was.
        Kind.Question -> Formula(
            kind,
            listOf(Slot.Word("Did"), Slot.Subject, Slot.Word("think (that)", null), Slot.Subject, would, verb),
            listOf(Slot.Word("[Сделал]"), Slot.Subject, Slot.Word("думать, что", null), Slot.Subject, Slot.Word("будет"), Slot.Word("мыть", Role.Verb)),
        )
        Kind.Reported -> Formula(
            kind,
            listOf(Slot.Subject, Slot.Word("asked whether", null), Slot.Subject, would, verb),
            listOf(Slot.Subject, Slot.Word("спросил, ли", null), Slot.Subject, Slot.Word("будет"), Slot.Word("мыть", Role.Verb)),
        )
    }
}

/** The formula of [kind] in this tense of [verb], or null for a tense the base closes as a whole voice or type. */
internal fun Tense.formula(kind: Kind, verb: String): Formula? {
    if (voice == null) return futureInPast(kind)
    val aspect = aspect ?: return null
    val time = time ?: return null
    if (verb == "be" && voice == Voice.Active && aspect == Aspect.Simple) be(time, kind)?.let { return it }
    return (if (voice == Voice.Active) active(aspect, time) else passive(aspect, time)).formula(kind)
}

// Reading a phrase.

private data class Token(val text: String, val word: String)

private val Contraction = Regex("""^([a-z]+)('m|'re|'s|'ve|'d|'ll)$""")

/** Words that take `'ll`, `'ve` and the rest onto themselves. `'s` after a noun is whose, and stays. */
private val Hosts = setOf("i", "you", "he", "she", "it", "we", "they", "there", "that", "who", "what", "where", "how")

private val Auxiliaries = setOf(
    "do", "does", "did", "will", "would", "shall", "am", "is", "are", "was", "were", "be", "being", "been",
    "have", "has", "had", "can", "could", "may", "might", "must", "should",
    "'m", "'re", "'s", "'ve", "'d", "'ll",
)

private val NegativeAuxiliaries = mapOf(
    "don't" to "do", "doesn't" to "does", "didn't" to "did", "won't" to "will", "wouldn't" to "would", "shan't" to "shall",
    "isn't" to "is", "aren't" to "are", "wasn't" to "was", "weren't" to "were",
    "haven't" to "have", "hasn't" to "has", "hadn't" to "had",
    "can't" to "can", "cannot" to "can", "couldn't" to "could", "mightn't" to "might", "mustn't" to "must",
    "shouldn't" to "should", "needn't" to "need",
)

private val Modals = setOf("can", "could", "may", "might", "must", "should", "need")

/** What stands inside a verb's group as well as around it: `has already been`, `had never`. */
private val Adverbs = setOf(
    "already", "never", "just", "still", "always", "ever", "often", "usually", "also", "really", "finally", "recently",
    "completely", "sometimes", "soon", "seldom", "rarely", "probably", "even", "only", "hardly", "long",
)

private val NegativeWords = setOf("never", "nothing", "no", "none", "nobody", "neither", "nowhere", "not")

private val QuestionWords = setOf("what", "when", "where", "why", "how", "who", "whom", "whose", "which")

private val Pronouns = setOf("i", "you", "he", "she", "it", "we", "they")

private val BeForms = setOf("am", "is", "are", "was", "were")

private val Determiners = setOf(
    "the", "a", "an", "my", "your", "his", "her", "its", "our", "their", "this", "that", "these", "those",
    "every", "each", "no", "some", "any", "all", "both",
)

private val BeforeDeterminers = setOf("all", "both", "not", "half")

/** `none of this`, `one of the boys`. */
private val OfWhich = setOf("none", "some", "one", "each", "most", "all", "many", "few")

/** Where a subject cannot reach: times, prepositions, conjunctions and the verbs that tell what was said. */
private val Stops = setOf(
    "yesterday", "today", "tomorrow", "now", "then", "tonight", "last", "next", "ago", "later",
    "by", "at", "in", "on", "before", "after", "during", "since", "for", "from", "to", "until", "till", "with",
    "when", "while", "if", "because", "whether", "that", "and", "but", "so",
    "said", "say", "says", "knew", "know", "thought", "think", "asked", "ask", "told", "tell", "promised", "hoped",
    "expected", "believed", "sure",
)

private val Times = setOf(
    "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "noon", "midnight",
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
)

/** A time that is no subject after these: `every summer people…`, but `the season does not…`. */
private val TimeNouns = setOf("summer", "winter", "spring", "autumn", "fall", "week", "weekend", "month", "year", "day", "morning", "evening", "night", "season")

private val BeforeTimes = setOf("every", "each", "last", "next", "this", "that", "all", "by", "at", "in", "on", "during", "for", "since")

private val Asking = setOf("ask", "asks", "asked", "wonder", "wondered", "wonders")

/** A word without its punctuation and its case, `’` as `'`: `"Didn’t` is `didn't`. */
private fun normal(raw: String): String =
    raw.replace('’', '\'').lowercase().trim { !it.isLetterOrDigit() && it != '\'' }.trim('\'')

private fun tokens(english: String): List<Token> = buildList {
    english.split(' ').filter { it.isNotEmpty() }.forEachIndexed { index, raw ->
        val shown = if (index == 0) raw else " $raw"
        val word = normal(raw)
        val contraction = Contraction.matchEntire(word)
        val apostrophe = raw.indexOfFirst { it == '\'' || it == '’' }
        if (contraction != null && contraction.groupValues[1] in Hosts && apostrophe > 0) {
            val cut = shown.length - (raw.length - apostrophe)
            add(Token(shown.substring(0, cut), contraction.groupValues[1]))
            add(Token(shown.substring(cut), contraction.groupValues[2]))
        } else {
            add(Token(shown, word))
        }
    }
}

private val Contracted = mapOf("'m" to "am", "'re" to "are", "'s" to "is, has", "'ve" to "have", "'d" to "would, had", "'ll" to "will")

/** What a short form stands for: `didn't` is `did not`, `'ll` is `will`. Null for a word written out. */
internal fun spelledOut(written: String): String? {
    // Not `normal`: it would take the apostrophe off `'ll`.
    val word = written.replace('’', '\'').lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
    NegativeAuxiliaries[word]?.let { return "$it not" }
    return Contracted[word]
}

private fun isAuxiliary(word: String) = word in Auxiliaries || word in NegativeAuxiliaries

private fun isQuestion(english: String) = english.trimEnd().trimEnd('"', '»', '”', '\'', ')').endsWith('?')

/**
 * Reads [english], a phrase of [verb] in [tense]. Its verb is the last word that is one of the verb's forms, and its
 * group is the auxiliaries, `not` and the adverbs straight before it. A question has its first auxiliary in front, and
 * the subject between that and the rest of the group; a statement has the subject straight before the group, as far as
 * its determiner. Whatever does not fit stays without a role, and the kind is still read.
 */
internal fun parsePhrase(english: String, verb: Verb, tense: Tense): Breakdown {
    val tokens = tokens(english)
    val words = tokens.map { it.word }
    val forms = verb.forms.mapValues { (_, variants) -> variants.map { normal(it.word) }.toSet() }
    val own = forms.values.flatten().toSet() + normal(verb.word)
    fun formOf(word: String): String? = when {
        word in own -> word
        word.endsWith("n't") && word.removeSuffix("n't") in own -> word.removeSuffix("n't")
        else -> null
    }
    val question = isQuestion(english)
    val at = words.indexOfLast { formOf(it) != null }
    if (at < 0) return Breakdown(kindOf(words, question, negative = false, groupStart = words.size), tokens.map { Piece(it.text) })

    val roles = arrayOfNulls<Role>(tokens.size)
    roles[at] = Role.Verb
    var i = at - 1
    val adverbs = mutableListOf<Int>()
    while (i >= 0) {
        val word = words[i]
        when {
            word == "not" || word in NegativeAuxiliaries -> roles[i] = Role.Not
            word in Auxiliaries -> roles[i] = Role.Auxiliary
            word in Adverbs -> adverbs += i
            else -> break
        }
        i--
    }
    adverbs.forEach { roles[it] = Role.Adverb }
    // `be` and its own `not`: `The sail is not ready`.
    if (words[at] in BeForms && words.getOrNull(at + 1) == "not") roles[at + 1] = Role.Not
    val start = roles.indexOfFirst { it != null }

    if (question) {
        val front = (0 until start).firstOrNull { isAuxiliary(words[it]) }
        if (front != null) {
            roles[front] = if (words[front] in NegativeAuxiliaries) Role.Not else Role.Auxiliary
            if (words[0] in QuestionWords) (0 until front).forEach { roles[it] = Role.QuestionWord }
            val between = (front + 1 until start).toList()
            val inOrder = between.isNotEmpty() && between.size <= 5 && between.none { words[it] in Stops }
            (if (inOrder) between else subjectBefore(words, start)).forEach { roles[it] = Role.Subject }
        } else if (start == 0) {
            // The verb itself in front, as `be` goes: `Are you in the harbor?`
            if (words.getOrNull(at + 1) in Pronouns) roles[at + 1] = Role.Subject
        } else if (words[0] in QuestionWords) {
            // The question word is the subject: `Who abandoned the ship?`
            (0 until start).forEach { roles[it] = Role.QuestionWord }
        } else {
            subjectBefore(words, start).forEach { roles[it] = Role.Subject }
        }
    } else {
        subjectBefore(words, start).forEach { roles[it] = Role.Subject }
    }

    val group = roles.indices.filter { roles[it] in setOf(Role.Auxiliary, Role.Not, Role.Adverb, Role.Verb) }
    val negative = group.any { roles[it] == Role.Not } || words[at].endsWith("n't")
    val modal = group.map { NegativeAuxiliaries[words[it]] ?: words[it] }.firstOrNull { it in Modals && it != words[at] }
    val kind = kindOf(words, question, negative, start)
    val negativeWord = if (kind == Kind.Negative && !negative) words.firstOrNull { it in NegativeWords } else null
    val word = formOf(words[at])!!
    val candidates = FormKind.entries.filter { word in forms[it].orEmpty() }
    val expected = tense.formula(kind, verb.word)?.english?.filterIsInstance<Slot.Form>()?.flatMap { it.kinds }.orEmpty()
    return Breakdown(
        kind = kind,
        pieces = tokens.mapIndexed { index, token -> Piece(token.text, roles[index]) },
        form = candidates.firstOrNull { it in expected } ?: candidates.firstOrNull(),
        negativeWord = negativeWord,
        modal = modal,
    )
}

private fun kindOf(words: List<String>, question: Boolean, negative: Boolean, groupStart: Int): Kind = when {
    question -> Kind.Question
    (0 until groupStart).any { words[it] == "whether" || (words[it] == "if" && words.getOrNull(it - 1) in Asking) } -> Kind.Reported
    negative || words.any { it in NegativeWords } -> Kind.Negative
    else -> Kind.Statement
}

/** The subject straight before [start]: a pronoun, or the words back to the first determiner, if no time stops them. */
private fun subjectBefore(words: List<String>, start: Int): List<Int> {
    var k = start - 1
    if (k < 0) return emptyList()
    if (words[k] in Pronouns) return listOf(k)
    val subject = ArrayDeque<Int>()
    while (k >= 0) {
        val word = words[k]
        if (word in Stops || word in Times || word.all { it.isDigit() }) break
        if (word in TimeNouns && words.getOrNull(k - 1) in BeforeTimes) break
        subject.addFirst(k)
        k--
        if (word in Determiners) {
            while (k >= 0 && words[k] in BeforeDeterminers) subject.addFirst(k--)
            if (k >= 1 && words[k] == "of" && words[k - 1] in OfWhich) {
                subject.addFirst(k)
                subject.addFirst(k - 1)
            }
            break
        }
    }
    return if (subject.size in 1..5) subject.toList() else emptyList()
}

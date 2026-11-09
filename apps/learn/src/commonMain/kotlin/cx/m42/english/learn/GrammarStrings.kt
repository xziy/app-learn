package cx.m42.english.learn

/** The words of the line that says which form a phrase is in, and of the page it opens, one table per language. */
internal data class GrammarStrings(
    /** What the cap opens, for those who cannot see it. */
    val open: String,
    val kinds: Map<Kind, String>,
    val types: Map<Aspect, String>,
    /** The page itself. */
    val page: RuleTexts,
) {
    /** `Тип 1 · Прошедшее · Вопрос`: what the line says first. */
    fun formLine(tense: Tense, kind: Kind, strings: LearnStrings): String = listOfNotNull(
        if (tense.voice == null) strings.futureInPast else tense.aspect?.let { types.getValue(it) },
        tense.time?.let { strings.timeNames.getValue(it) },
        kinds.getValue(kind),
    ).joinToString(" · ")

    /** `Актив · Past Simple`: what it says dimmer after that. */
    fun voiceLine(tense: Tense, strings: LearnStrings): String =
        listOfNotNull(tense.voice?.let { strings.voiceNames.getValue(it) }, tense.englishName()).joinToString(" · ")

    companion object {
        val Ru = GrammarStrings(
            open = "Как строится эта фраза",
            kinds = mapOf(
                Kind.Statement to "Утверждение",
                Kind.Negative to "Отрицание",
                Kind.Question to "Вопрос",
                Kind.Reported to "Косвенный вопрос",
            ),
            types = mapOf(
                Aspect.Simple to "Тип 1",
                Aspect.Continuous to "Тип 2",
                Aspect.Perfect to "Тип 3",
                Aspect.PerfectContinuous to "Тип 4",
            ),
            page = RussianRules,
        )

        val En = GrammarStrings(
            open = "How this phrase is built",
            kinds = mapOf(
                Kind.Statement to "Statement",
                Kind.Negative to "Negative",
                Kind.Question to "Question",
                Kind.Reported to "Reported question",
            ),
            types = mapOf(
                Aspect.Simple to "Type 1",
                Aspect.Continuous to "Type 2",
                Aspect.Perfect to "Type 3",
                Aspect.PerfectContinuous to "Type 4",
            ),
            page = EnglishRules,
        )
    }
}

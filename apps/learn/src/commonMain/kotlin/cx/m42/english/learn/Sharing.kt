package cx.m42.english.learn

/**
 * Handing a text to whatever the person sends it with: ChatGPT, a messenger, notes. After speech the other thing each
 * platform does its own way. A phone has the system's menu for it; a desktop has none Java can reach, so there the
 * text is copied, to be pasted by hand.
 */
internal interface Sharing {
    /** True where [share] opens the system's menu, false where it only copies. */
    val viaMenu: Boolean

    /** Hands [text] to the system's menu, or to the clipboard. False if neither could be done. */
    fun share(text: String): Boolean
}

internal expect fun platformSharing(): Sharing

/**
 * What a phrase is, written out for someone who does not see the card: a chat bot asked why it is built so, or a friend
 * in a messenger. It begins with the question, so that what is sent is a request and not only a quotation; the person
 * may change any of it before sending. The thought and the Russian are Russian in either language.
 */
internal fun askPrompt(entry: FeedPage.Phrase, theme: Theme?, strings: LearnStrings): String {
    val ask = strings.ask
    val texts = strings.grammar.page
    val verb = entry.verb
    val tense = entry.tense
    val sentence = entry.sentence
    val phrase = parsePhrase(sentence.english, verb, tense)
    val formula = tense.formula(phrase.kind, verb.word)
    return buildString {
        fun line(label: String, text: String) = append(label).append(": ").append(text).append('\n')

        append(ask.intro).append("\n\n")
        line(ask.sentence, sentence.english)
        line(ask.translation, sentence.ours)
        line(ask.thought, sentence.thought)
        if ('[' in sentence.thought) append(ask.brackets).append('\n')
        line(ask.sound, plainStress(sentence.transcription) + " " + ask.stress)
        append('\n')
        line(ask.verb, "${verb.word} — ${verb.meaning}")
        val forms = FormKind.entries.mapNotNull { kind -> verb.forms[kind]?.joinToString("/") { it.word } }
        if (forms.isNotEmpty()) line(ask.forms, forms.joinToString(", "))
        line(ask.tense, "${strings.grammar.formLine(tense, phrase.kind, strings)} (${strings.grammar.voiceLine(tense, strings)})")
        if (formula != null) {
            val signs = texts.legend(formula, verb, tense).filter { it.key.startsWith("form-") }
            val where = if (signs.isEmpty()) "" else ", ${ask.where} " + signs.joinToString("; ") { "${it.sign} — ${it.text}" }
            line(ask.formula, plainFormula(formula.english, texts.subjectOf(tense), formula.question) + where)
        }
        if (sentence.nouns.isNotEmpty()) {
            line(
                ask.nouns,
                sentence.nouns.joinToString("; ") { noun ->
                    val written = if (noun.form.equals(noun.word, ignoreCase = true)) noun.word else "${noun.form} (${noun.word})"
                    if (noun.meaning.isBlank()) written else "$written — ${noun.meaning}"
                },
            )
        }
        if (theme != null && theme.name != DefaultTheme) line(ask.theme, strings.themeTitle(theme))
        append('\n')
        append(ask.request)
    }
}

/** `Did + кто + V + …?` as plain text: what the cap's page shows in colours. */
internal fun plainFormula(slots: List<Slot>, subject: String, question: Boolean): String =
    slots.joinToString(" + ") { it.shown(subject) } + " + …" + if (question) "?" else ""

private val StressMarks = Regex("""\*\*(.+?)\*\*""")

/** `[эб**æ**ндэн]` as `[эбÆндэн]`: plain text has no bold, and a capital letter reads as the stress anywhere. */
internal fun plainStress(transcription: String): String =
    transcription.replace(StressMarks) { it.groupValues[1].uppercase() }

/** The words of asking about a phrase, one table per language. */
internal data class AskStrings(
    /** The button on a phrase, for those who cannot see it. */
    val open: String,
    val title: String,
    val about: String,
    val field: String,
    val share: String,
    val copy: String,
    /** Under the button: what pressing it does. */
    val viaMenu: String,
    val viaCopy: String,
    val copied: String,
    val failed: String,
    val reset: String,
    // The prompt.
    val intro: String,
    val sentence: String,
    val translation: String,
    val thought: String,
    val brackets: String,
    val sound: String,
    val stress: String,
    val verb: String,
    val forms: String,
    val tense: String,
    val formula: String,
    val where: String,
    val nouns: String,
    val theme: String,
    val request: String,
) {
    companion object {
        val Ru = AskStrings(
            open = "Спросить о фразе",
            title = "Спросить о фразе",
            about = "Этот текст уйдёт вместе с фразой в ChatGPT, мессенджер или куда выберете. Его можно поправить.",
            field = "Промпт",
            share = "Поделиться",
            copy = "Скопировать",
            viaMenu = "Откроется меню «Поделиться»: выберите ChatGPT, мессенджер или другое приложение",
            viaCopy = "Здесь нет меню «Поделиться», поэтому текст копируется: вставьте его в ChatGPT или в мессенджер",
            copied = "Скопировано",
            failed = "Не получилось. Выделите текст и скопируйте его сами",
            reset = "Вернуть исходный текст",
            intro = "Объясни мне, почему это английское предложение построено именно так.",
            sentence = "Предложение",
            translation = "По-русски",
            thought = "Как думает англичанин, слово в слово",
            brackets = "В квадратных скобках — слова, которых по-русски не говорят, а по-английски они есть.",
            sound = "Произношение русскими буквами",
            stress = "(заглавная буква — ударный звук)",
            verb = "Глагол",
            forms = "Его формы",
            tense = "Время",
            formula = "Схема",
            where = "где",
            nouns = "Существительные",
            theme = "Тема примеров",
            request = "Разбери предложение подробно: почему здесь это время, такой порядок слов и такая форма глагола, " +
                "зачем каждое служебное слово, артикли и предлоги. Чем оно отличается от того, как сказали бы по-русски, " +
                "и где тут легко ошибиться? Приведи 2–3 похожих предложения с переводом.",
        )

        val En = AskStrings(
            open = "Ask about the phrase",
            title = "Ask about the phrase",
            about = "This text goes with the phrase to ChatGPT, a messenger or wherever you choose. It can be edited.",
            field = "Prompt",
            share = "Share",
            copy = "Copy",
            viaMenu = "The share menu opens: choose ChatGPT, a messenger or another app",
            viaCopy = "There is no share menu here, so the text is copied: paste it into ChatGPT or a messenger",
            copied = "Copied",
            failed = "It did not work. Select the text and copy it yourself",
            reset = "Back to the original text",
            intro = "Explain to me why this English sentence is built the way it is.",
            sentence = "Sentence",
            translation = "In Russian",
            thought = "How the English think, word for word in Russian",
            brackets = "Words in square brackets are those Russian does not say and English does.",
            sound = "Pronunciation in Russian letters",
            stress = "(a capital letter is the stressed sound)",
            verb = "Verb",
            forms = "Its forms",
            tense = "Tense",
            formula = "Pattern",
            where = "where",
            nouns = "Nouns",
            theme = "Theme of the examples",
            request = "Take the sentence apart in detail: why this tense, this word order and this form of the verb, what " +
                "each helper word, article and preposition is for. How does it differ from the way Russian says it, and " +
                "where is it easy to go wrong? Give 2–3 similar sentences with a translation.",
        )
    }
}

package cx.m42.english.books

import cx.m42.superizer.runtime.LocaleService

/** The app's words, one table per language. */
internal data class BooksStrings(
    val shelfTitle: String,
    val empty: String,
    val close: String,
    val back: String,
    val settings: String,
    val contents: String,
    val notStarted: String,
    val read: (percent: Int) -> String,
    val sentences: (total: Int, translated: Int) -> String,
    val cannotOpen: String,
    val untranslated: String,
    val end: String,
    val linesTitle: String,
    val linesHint: String,
    val line: (Line) -> String,
    val noTranscription: String,
    val paperTitle: String,
    val paper: (Paper) -> String,
    val fontTitle: String,
    val smaller: String,
    val larger: String,
    val serif: String,
    val sans: String,
    val nouns: String,
    val nounsHint: String,
    val here: String,
    val inLine: (Line) -> String,
    val otherSenses: String,
    val plural: String,
    val countable: (String) -> String?,
    val number: (String) -> String?,
    val inBook: (Int) -> String,
    val notInDictionary: String,
    val notes: String,
    val done: String,
) {
    companion object {
        val Ru = BooksStrings(
            shelfTitle = "Книги",
            empty = "Книг пока нет",
            close = "Закрыть",
            back = "Назад",
            settings = "Как читать",
            contents = "Оглавление",
            notStarted = "Не начата",
            read = { "Прочитано $it%" },
            sentences = { total, translated ->
                val all = ruCount(total, "фраза", "фразы", "фраз")
                if (translated >= total) "$all · переведено всё" else "$all · переведено $translated"
            },
            cannotOpen = "Эту книгу не удалось открыть",
            untranslated = "Ещё не переведено",
            end = "Конец",
            linesTitle = "Строки",
            linesHint = "Одна строка — текст идёт сплошь, как в обычной книге. Тап по фразе показывает её остальные строки, " +
                "тап по выделенному слову — его перевод.",
            line = {
                when (it) {
                    Line.Ours -> "Как думаем мы"
                    Line.Thought -> "Как думает англичанин"
                    Line.English -> "Как правильно"
                    Line.Transcription -> "Транскрипция"
                }
            },
            noTranscription = "В этой книге нет транскрипции",
            paperTitle = "Страница",
            paper = {
                when (it) {
                    Paper.Light -> "Светлая"
                    Paper.Sepia -> "Тёплая"
                    Paper.Dark -> "Тёмная"
                }
            },
            fontTitle = "Шрифт",
            smaller = "Мельче",
            larger = "Крупнее",
            serif = "С засечками",
            sans = "Без засечек",
            nouns = "Выделять существительные",
            nounsHint = "У каждого существительного свой цвет, один и тот же во всех строках фразы.",
            here = "Здесь",
            inLine = {
                when (it) {
                    Line.Ours -> "По-русски"
                    Line.Thought -> "В мысли"
                    Line.English -> "В тексте"
                    Line.Transcription -> "Звучит"
                }
            },
            otherSenses = "Другие значения",
            plural = "Множественное число",
            countable = {
                when (it) {
                    "yes" -> "Исчисляемое"
                    "no" -> "Неисчисляемое"
                    "both" -> "Бывает исчисляемым и неисчисляемым"
                    else -> null
                }
            },
            number = {
                when (it) {
                    "plural_only" -> "Только во множественном числе"
                    "same" -> "Множественное число такое же"
                    "collective" -> "Собирательное"
                    else -> null
                }
            },
            inBook = { "В книге: ${ruCount(it, "раз", "раза", "раз")}" },
            notInDictionary = "В словаре этого слова пока нет",
            notes = "Заметки",
            done = "Готово",
        )

        val En = BooksStrings(
            shelfTitle = "Books",
            empty = "No books yet",
            close = "Close",
            back = "Back",
            settings = "Reading",
            contents = "Contents",
            notStarted = "Not started",
            read = { "$it% read" },
            sentences = { total, translated ->
                val all = if (total == 1) "1 sentence" else "$total sentences"
                if (translated >= total) "$all · all translated" else "$all · $translated translated"
            },
            cannotOpen = "This book could not be opened",
            untranslated = "Not translated yet",
            end = "The end",
            linesTitle = "Lines",
            linesHint = "With one line the text runs on as in any book. A tap on a sentence shows its other lines, a tap on " +
                "a coloured word its translation.",
            line = {
                when (it) {
                    Line.Ours -> "How we think"
                    Line.Thought -> "How the English think"
                    Line.English -> "How it is said"
                    Line.Transcription -> "Transcription"
                }
            },
            noTranscription = "This book has no transcription",
            paperTitle = "Page",
            paper = {
                when (it) {
                    Paper.Light -> "Light"
                    Paper.Sepia -> "Warm"
                    Paper.Dark -> "Dark"
                }
            },
            fontTitle = "Type",
            smaller = "Smaller",
            larger = "Larger",
            serif = "Serif",
            sans = "Sans",
            nouns = "Set off the nouns",
            nounsHint = "Each noun has a colour of its own, the same in every line of its sentence.",
            here = "Here",
            inLine = {
                when (it) {
                    Line.Ours -> "In Russian"
                    Line.Thought -> "In the thought"
                    Line.English -> "In the text"
                    Line.Transcription -> "Sounds"
                }
            },
            otherSenses = "Other senses",
            plural = "Plural",
            countable = {
                when (it) {
                    "yes" -> "Countable"
                    "no" -> "Uncountable"
                    "both" -> "Countable and uncountable"
                    else -> null
                }
            },
            number = {
                when (it) {
                    "plural_only" -> "Plural only"
                    "same" -> "The plural is the same"
                    "collective" -> "Collective"
                    else -> null
                }
            },
            inBook = { if (it == 1) "In the book: once" else "In the book: $it times" },
            notInDictionary = "The dictionary does not have this word yet",
            notes = "Notes",
            done = "Done",
        )

        fun of(locale: LocaleService): BooksStrings = locale.pick(mapOf("ru" to Ru, "en" to En), fallback = Ru)
    }
}

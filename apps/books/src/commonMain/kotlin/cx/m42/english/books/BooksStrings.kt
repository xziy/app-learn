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
    val addBook: String,
    val fetchTitle: String,
    val fetchHint: String,
    val linkPlaceholder: String,
    val linkLabel: String,
    val scan: String,
    val download: String,
    val downloading: String,
    val outcome: (Outcome, title: String) -> String,
    val readNow: String,
    val problem: (FetchProblem, status: Int?) -> String,
    /** Where the downloaded books are: [location] is a path, or on iOS the folder in Files. */
    val folder: (location: String, inFiles: Boolean) -> String,
    val downloaded: String,
    val remove: String,
    val removeConfirm: String,
    val keep: String,
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
            addBook = "Загрузить книгу",
            fetchTitle = "Загрузка книги",
            fetchHint = "Вставьте ссылку на книгу — файл .book.tgz — или прочитайте её QR-код.",
            linkPlaceholder = "https://…/книга.book.tgz",
            linkLabel = "Ссылка на книгу",
            scan = "Прочитать QR-код",
            download = "Скачать",
            downloading = "Скачиваю…",
            outcome = { outcome, title ->
                when (outcome) {
                    Outcome.Added -> "«$title» на полке"
                    Outcome.Updated -> "«$title» обновлена"
                    Outcome.Same -> "«$title» уже на полке, эта же версия"
                }
            },
            readNow = "Читать",
            problem = { problem, status ->
                when (problem) {
                    FetchProblem.NotALink -> "Это не ссылка на книгу. Ссылка начинается с https://"
                    FetchProblem.NoAnswer -> "Не удалось скачать: нет ответа. Проверьте интернет и ссылку."
                    FetchProblem.Status -> "Не удалось скачать: сервер ответил $status."
                    FetchProblem.NotABook -> "По этой ссылке не книга. Нужен файл .book.tgz."
                    FetchProblem.Damaged -> "Файл книги повреждён. Скачайте его ещё раз."
                    FetchProblem.Newer -> "Эта книга для новой версии приложения. Обновите приложение."
                    FetchProblem.TooLarge -> "Файл слишком большой для книги."
                    FetchProblem.CannotSave -> "Не удалось сохранить книгу на устройстве."
                    FetchProblem.Unavailable -> "Здесь книги не скачиваются."
                }
            },
            folder = { location, inFiles ->
                val where = if (inFiles) "Файлы › На iPhone › " + location.replace("/", " › ") else location
                "Скачанные книги лежат в папке: $where. Туда можно положить файл .book.tgz и самому."
            },
            downloaded = "Скачана",
            remove = "Удалить",
            removeConfirm = "Удалить книгу",
            keep = "Оставить",
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
            addBook = "Add a book",
            fetchTitle = "Add a book",
            fetchHint = "Paste a link to a book — a .book.tgz file — or read its QR code.",
            linkPlaceholder = "https://…/book.book.tgz",
            linkLabel = "Link to the book",
            scan = "Read a QR code",
            download = "Download",
            downloading = "Downloading…",
            outcome = { outcome, title ->
                when (outcome) {
                    Outcome.Added -> "“$title” is on the shelf"
                    Outcome.Updated -> "“$title” is updated"
                    Outcome.Same -> "“$title” is on the shelf already, the same version"
                }
            },
            readNow = "Read",
            problem = { problem, status ->
                when (problem) {
                    FetchProblem.NotALink -> "This is not a link to a book. A link starts with https://"
                    FetchProblem.NoAnswer -> "Could not download: no answer. Check the connection and the link."
                    FetchProblem.Status -> "Could not download: the server answered $status."
                    FetchProblem.NotABook -> "This link is not a book. It has to be a .book.tgz file."
                    FetchProblem.Damaged -> "The book's file is damaged. Download it again."
                    FetchProblem.Newer -> "This book is for a newer version of the app. Update the app."
                    FetchProblem.TooLarge -> "The file is too large for a book."
                    FetchProblem.CannotSave -> "Could not save the book on this device."
                    FetchProblem.Unavailable -> "Books cannot be downloaded here."
                }
            },
            folder = { location, inFiles ->
                val where = if (inFiles) "Files › On My iPhone › " + location.replace("/", " › ") else location
                "Downloaded books are in the folder $where. A .book.tgz file put there by hand shows too."
            },
            downloaded = "Downloaded",
            remove = "Delete",
            removeConfirm = "Delete the book",
            keep = "Keep",
        )

        fun of(locale: LocaleService): BooksStrings = locale.pick(mapOf("ru" to Ru, "en" to En), fallback = Ru)
    }
}

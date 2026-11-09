package cx.m42.english.learn

import cx.m42.superizer.runtime.LocaleService

/** The app's words, one table per language. */
internal data class LearnStrings(
    val ours: String,
    val thought: String,
    val english: String,
    val say: String,
    val play: String,
    val pause: String,
    val noSpeech: String,
    val close: String,
    val hint: String,
    val done: String,
    val toList: String,
    val empty: String,
    val listTitle: String,
    val start: String,
    val resume: String,
    val noneChosen: String,
    val pick: String,
    val allSeen: String,
    val seenTitle: String,
    val seenNone: String,
    val seenHint: String,
    val unsee: String,
    val unseeAll: String,
    val unseeAllSure: String,
    val againDone: String,
    val addTitle: String,
    val add: String,
    val remove: String,
    val about: String,
    val back: String,
    val meaningsTitle: String,
    val formsTitle: String,
    val formBase: String,
    val formPast: String,
    val formParticiple: String,
    val formIng: String,
    val formS: String,
    val notesTitle: String,
    val signsTitle: String,
    val signs: List<String>,
    val voiceNames: Map<Voice, String>,
    val voiceAbout: Map<Voice, String>,
    val aspectNames: Map<Aspect, String>,
    val aspectAbout: Map<Aspect, String>,
    val timeNames: Map<Time, String>,
    val timeAbout: Map<Time, String>,
    val futureInPast: String,
    val futureInPastAbout: String,
    val notPossible: String,
    val portion: (Portion) -> String,
    val themesTitle: String,
    /**
     * The base's own prose is in Russian only: what a theme is about, why a tense does not exist. In another language
     * a theme is just its name, and a closed tense says no more than that it is closed.
     */
    val readsRussian: Boolean,
    /** The cap's page: which form a phrase is in and how it is built. */
    val grammar: GrammarStrings,
    /** The dictionary and a word's page. */
    val words: WordStrings,
) {
    fun themeTitle(theme: Theme): String = if (readsRussian) theme.russianTitle else theme.englishTitle

    fun themeAbout(theme: Theme): String? = if (readsRussian) theme.russianAbout else null

    fun step(step: Step): String = when (step) {
        Step.Ours -> ours
        Step.Thought -> thought
        Step.English -> english
    }

    fun form(kind: FormKind): String = when (kind) {
        FormKind.Base -> formBase
        FormKind.Past -> formPast
        FormKind.Participle -> formParticiple
        FormKind.Ing -> formIng
        FormKind.S -> formS
    }

    /** The biggest line of a tense's card: what is being closed or begun. */
    fun tenseTitle(tense: Tense): String = when {
        tense.voice == null -> futureInPast
        tense.aspect == null -> voiceNames.getValue(tense.voice)
        else -> aspectNames.getValue(tense.aspect)
    }

    /** The line above it; none where the title is the voice itself. */
    fun tenseVoice(tense: Tense): String? =
        if (tense.voice != null && tense.aspect != null) voiceNames.getValue(tense.voice) else null

    fun tenseTime(tense: Tense): String? = tense.time?.let { timeNames.getValue(it) }

    fun tenseAbout(tense: Tense): String =
        if (tense.voice == null) {
            futureInPastAbout
        } else {
            listOfNotNull(
                voiceAbout.getValue(tense.voice),
                tense.aspect?.let { aspectAbout.getValue(it) },
                tense.time?.let { timeAbout.getValue(it) },
            ).joinToString(" ")
        }

    companion object {
        private fun russianCards(count: Int): String = when {
            count % 100 in 11..14 -> "карточек"
            count % 10 == 1 -> "карточка"
            count % 10 in 2..4 -> "карточки"
            else -> "карточек"
        }

        val Ru = LearnStrings(
            ours = "Как думаем мы",
            thought = "Как думает англичанин",
            english = "Как правильно",
            say = "Сказать",
            play = "Воспроизвести",
            pause = "Пауза",
            noSpeech = "Озвучить невозможно: нет ни записи, ни синтезатора речи",
            close = "Закрыть",
            hint = "Нажмите, чтобы идти дальше\nИли листайте: вверх — дальше, влево — по фразе",
            done = "Выбранные глаголы пройдены",
            toList = "К списку глаголов",
            empty = "Пока нет слов",
            listTitle = "Выбранные глаголы",
            start = "Начать",
            resume = "Продолжить",
            noneChosen = "Пока ничего не выбрано. Добавьте глаголы из списка ниже",
            pick = "Выбрать $WordsAtOnce случайных",
            allSeen = "Все глаголы просмотрены",
            seenTitle = "Просмотренные",
            seenNone = "Пока ничего нет. Глагол попадает сюда, когда лента прошла его до конца",
            seenHint = "Нажмите, чтобы посмотреть заново. Смахните, чтобы отменить просмотр",
            unsee = "Отменить просмотр",
            unseeAll = "Отменить просмотр всех",
            unseeAllSure = "Нажмите ещё раз, чтобы отменить все",
            againDone = "Глагол пройден",
            addTitle = "Добавить",
            add = "Добавить глагол",
            remove = "Убрать глагол",
            about = "О глаголе",
            back = "Назад",
            meaningsTitle = "Значения",
            formsTitle = "Формы",
            formBase = "Начальная форма",
            formPast = "Прошедшее время",
            formParticiple = "Пассивное определение",
            formIng = "Форма с -ing",
            formS = "Форма с -s",
            notesTitle = "Особенности",
            signsTitle = "Знаки транскрипции",
            signs = listOf(
                "æ — между а и э (bad)",
                "œ: — долгий, похож на ё (girl)",
                "θ — глухое th (think)",
                "ð — звонкое th (this)",
                "w — губы почти сомкнуты (we)",
                "ŋ — носовое ng (sing)",
                "Двоеточие — долгий звук, жирная буква — ударение",
            ),
            voiceNames = mapOf(Voice.Active to "Актив", Voice.Passive to "Пассив"),
            voiceAbout = mapOf(
                Voice.Active to "Подлежащее само делает действие.",
                Voice.Passive to "Действие делают над подлежащим.",
            ),
            aspectNames = mapOf(
                Aspect.Simple to "Тип 1. Разово или факт (Simple)",
                Aspect.Continuous to "Тип 2. Непрерывно (Continuous)",
                Aspect.Perfect to "Тип 3. К моменту (Perfect)",
                Aspect.PerfectContinuous to "Тип 4. Непрерывно до момента (Perfect Continuous)",
            ),
            aspectAbout = mapOf(
                Aspect.Simple to "Просто факт или то, что бывает регулярно: вчера, каждый день, завтра.",
                Aspect.Continuous to "Действие идёт, важен сам процесс: сейчас, весь день, с трёх до семи.",
                Aspect.Perfect to "Действие готово к какому-то моменту, важен результат: уже, к пяти.",
                Aspect.PerfectContinuous to "Действие длится какое-то время и доходит до момента: целых два часа.",
            ),
            timeNames = mapOf(Time.Past to "Прошедшее", Time.Present to "Настоящее", Time.Future to "Будущее"),
            timeAbout = mapOf(
                Time.Past to "Говорим о том, что было.",
                Time.Present to "Говорим о том, что есть.",
                Time.Future to "Говорим о том, что будет.",
            ),
            futureInPast = "Будущее в прошедшем",
            futureInPastAbout = "Будущее, если смотреть на него из прошлого: я знал, что он это сделает. Строится с would.",
            notPossible = "Так не говорят",
            portion = { "Порция ${it.number} из ${it.total} · ${it.cards} ${russianCards(it.cards)}" },
            themesTitle = "Тематика",
            readsRussian = true,
            grammar = GrammarStrings.Ru,
            words = WordStrings.Ru,
        )

        val En = LearnStrings(
            ours = "How we think",
            thought = "How the English think",
            english = "How to say it",
            say = "Say it",
            play = "Play",
            pause = "Pause",
            noSpeech = "It cannot be said: there is no recording and no speech synthesizer",
            close = "Close",
            hint = "Tap to go on\nOr swipe: up for the next card, left along a phrase",
            done = "The chosen verbs are done",
            toList = "To the list of verbs",
            empty = "No words yet",
            listTitle = "Chosen verbs",
            start = "Start",
            resume = "Continue",
            noneChosen = "Nothing is chosen yet. Add verbs from the list below",
            pick = "Pick $WordsAtOnce at random",
            allSeen = "All the verbs are seen",
            seenTitle = "Seen",
            seenNone = "Nothing yet. A verb comes here once the feed has gone through it to its end",
            seenHint = "Tap to see it again. Swipe it away to mark it as not seen",
            unsee = "Mark as not seen",
            unseeAll = "Mark all as not seen",
            unseeAllSure = "Press again to mark them all",
            againDone = "The verb is done",
            addTitle = "Add",
            add = "Add the verb",
            remove = "Remove the verb",
            about = "About the verb",
            back = "Back",
            meaningsTitle = "Meanings",
            formsTitle = "Forms",
            formBase = "Base form",
            formPast = "Past",
            formParticiple = "Participle",
            formIng = "-ing form",
            formS = "-s form",
            notesTitle = "Particulars",
            signsTitle = "Transcription signs",
            signs = listOf(
                "æ: between а and э (bad)",
                "œ: long, close to ё (girl)",
                "θ: voiceless th (think)",
                "ð: voiced th (this)",
                "w: lips almost closed (we)",
                "ŋ: nasal ng (sing)",
                "A colon is a long sound, a bold letter the stress",
            ),
            voiceNames = mapOf(Voice.Active to "Active", Voice.Passive to "Passive"),
            voiceAbout = mapOf(
                Voice.Active to "The subject does the action.",
                Voice.Passive to "The action is done to the subject.",
            ),
            aspectNames = mapOf(
                Aspect.Simple to "Type 1. Once, or a fact (Simple)",
                Aspect.Continuous to "Type 2. Going on (Continuous)",
                Aspect.Perfect to "Type 3. By a moment (Perfect)",
                Aspect.PerfectContinuous to "Type 4. Going on up to a moment (Perfect Continuous)",
            ),
            aspectAbout = mapOf(
                Aspect.Simple to "A plain fact, or what happens regularly: yesterday, every day, tomorrow.",
                Aspect.Continuous to "The action is going on, the process is what counts: now, all day, from three to seven.",
                Aspect.Perfect to "The action is done by some moment, the result is what counts: already, by five.",
                Aspect.PerfectContinuous to "The action lasts a while and reaches a moment: for two hours.",
            ),
            timeNames = mapOf(Time.Past to "Past", Time.Present to "Present", Time.Future to "Future"),
            timeAbout = mapOf(
                Time.Past to "About what was.",
                Time.Present to "About what is.",
                Time.Future to "About what will be.",
            ),
            futureInPast = "Future in the past",
            futureInPastAbout = "The future as seen from the past: I knew that he would do it. Built with would.",
            notPossible = "Nobody says it this way",
            portion = { "Portion ${it.number} of ${it.total} · ${it.cards} ${if (it.cards == 1) "card" else "cards"}" },
            themesTitle = "Theme",
            readsRussian = false,
            grammar = GrammarStrings.En,
            words = WordStrings.En,
        )

        fun of(locale: LocaleService): LearnStrings = locale.pick(mapOf("ru" to Ru, "en" to En), fallback = Ru)
    }
}

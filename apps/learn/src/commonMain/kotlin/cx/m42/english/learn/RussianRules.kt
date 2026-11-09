package cx.m42.english.learn

/** The cap's page in Russian: see [RuleTexts]. */
internal object RussianRules : RuleTexts() {
    override val phraseTitle = "Эта фраза"
    override val translation = "По-русски"
    override val thoughtAbout = "Это та же фраза русскими словами, но в английском порядке, слово в слово. Слышно каждое " +
        "английское слово, даже то, которого по-русски не говорят. Проговорите фразу так, и останется заменить каждое " +
        "слово английским."
    override val partsTitle = "По частям"
    override val noBreakdown = "Разобрать эту фразу по частям не получилось: в ней не нашлась форма глагола."
    override val formulaTitle = "Схема"
    override val inEnglish = "По-английски"
    override val thoughtOnWash = "Как думает англичанин, на примере глагола «мыть»"
    override val rulesTitle = "Запомнить"
    override val glossaryTitle = "Слова и значки"
    override val glossary = listOf(
        "Тип 1" to "просто факт: разово, регулярно или всегда",
        "Тип 2" to "действие идёт, важен процесс: сейчас, весь день",
        "Тип 3" to "действие уже сделано, важен результат: уже, к пяти",
        "Тип 4" to "действие длится какое-то время до какого-то момента: целых два часа",
        "Актив" to "действие делает сам тот, о ком говорим: «я мою машину»",
        "Пассив" to "действие делают над ним: «машина вымыта»",
        "Служебное слово" to "do, did, will, have, be и их формы. Само почти ничего не значит, а показывает время и вид " +
            "фразы. В учебниках его называют вспомогательным глаголом",
        "1-я, 2-я, 3-я форма" to "три главные формы глагола: wash — washed — washed, go — went — gone. 1-я — как в " +
            "словаре, 2-я — прошедшее время, 3-я — «сделанный». У обычных глаголов 2-я и 3-я одинаковые, с -ed, у " +
            "неправильных — свои, их учат наизусть",
    )
    override val who = "кто"
    override val what = "что"
    override val and = "и"

    override fun whenSaid(tense: Tense) = ruWhenSaid(tense)
    override fun kindAbout(kind: Kind) = ruKindAbout(kind)
    override fun voiceAbout(tense: Tense) = ruVoiceAbout(tense)
    override fun names(tense: Tense) = ruNames(tense)

    override fun rules(phrase: Breakdown, tense: Tense, verb: String, english: String): List<String> =
        ruKindRules(phrase.kind, tense, verb) + ruTenseRules(tense) + ruPhraseRules(phrase, english)

    override val mainPart = "главная часть: кто сказал, знал, думал или спросил. За ней — то, что будет."
    override val frontPart = "когда, где или как: такие слова ставят в начало или в конец фразы."
    override val restPart = "всё остальное: кого, что, где, когда."

    override fun questionWordPart(several: Boolean, russian: String?): String {
        val gloss = russian?.let { " «$it»" } ?: ""
        return if (several) "вопросительные слова$gloss. Они стоят в самом начале." else "вопросительное слово$gloss. Оно стоит в самом начале."
    }

    override fun subjectPart(passive: Boolean, russian: String?): String {
        val gloss = russian?.let { ": «$it»" } ?: ""
        return if (passive) "кто или что, с ним это делают$gloss." else "кто делает$gloss."
    }

    override fun auxAbout(aux: Aux): String = when (aux.word) {
        "do" -> "служебное слово настоящего времени, после I, you, we, they"
        "does" -> "служебное слово настоящего времени, после he, she, it"
        "did" -> "служебное слово прошедшего времени"
        "will" -> "служебное слово будущего времени"
        "shall" -> "служебное слово будущего времени, после I и we"
        "would" -> "will, когда о будущем говорят из прошлого"
        "am" -> "be («быть») в настоящем времени, после I"
        "is" -> "be («быть») в настоящем времени, после he, she, it"
        "are" -> "be («быть») в настоящем времени, после you, we, they"
        "was" -> "be («быть») в прошедшем времени, после I, he, she, it"
        "were" -> "be («быть») в прошедшем времени, после you, we, they"
        "be" -> "be («быть») в 1-й форме"
        "been" -> "be («быть») в 3-й форме"
        "being" -> "be с -ing: действие идёт"
        "have" -> if (aux.base) "have («иметь») в 1-й форме" else "have («иметь»), после I, you, we, they"
        "has" -> "have («иметь»), после he, she, it"
        "had" -> "have («иметь») в прошедшем времени"
        else -> "модальный глагол, на месте служебного слова"
    }

    override fun inThought(thought: String): String =
        if (thought.startsWith("[")) "По-русски его не говорят, поэтому в мысли оно в квадратных скобках: «$thought»."
        else "В мысли — ${quoted(thought)}."

    override fun quoted(thought: String): String = if (thought.startsWith("«")) "$thought в кавычках" else "«$thought»"

    override fun writtenOut(full: String) = "это $full, "
    override val unknownAux = "служебное слово."
    override fun severalAux(each: String) = "служебные слова. В мысли: $each."
    override val bareNot = "«не». Оно стоит сразу после первого служебного слова, и в мысли «не» там же."

    override fun notWith(full: String, about: String?, thought: String?): String =
        if (about == null) "это $full: «не»." else "это $full: $about и not — «не». В мысли — «$thought не»."

    override fun adverbPart(russian: String?): String =
        (russian?.let { "«$it»" } ?: "наречие") + ". Такие слова стоят между служебным словом и глаголом, и в мысли там же."

    override fun verbPart(meaning: String, form: FormKind?, reason: Pair<Reason, String>?): String {
        val shape = when (form) {
            FormKind.Base -> " в 1-й форме, как в словаре"
            FormKind.Past -> " во 2-й форме: это прошедшее время"
            FormKind.Participle -> " в 3-й форме: «сделанный»"
            FormKind.Ing -> " с -ing: «делающий»"
            FormKind.S -> " с -s: так он стоит после he, she, it"
            null -> ""
        }
        val why = reason?.let { (cause, word) ->
            when (cause) {
                Reason.BaseAfterHelper -> " После $word глагол всегда в 1-й форме."
                Reason.ThirdAfterHave -> " После $word глагол в 3-й форме."
                Reason.ThirdAfterBe -> " После $word глагол в 3-й форме: так получается пассив."
                Reason.IngAfterBe -> " После $word глагол с -ing."
            }
        } ?: ""
        return "глагол «$meaning»$shape.$why"
    }

    override fun subjectLegend(passive: Boolean) =
        if (passive) "то, с чем это делают: my car, the letter" else "тот, кто делает: I, you, she, my brother"

    override fun formName(kind: FormKind) = when (kind) {
        FormKind.Base -> "1-я форма, как в словаре"
        FormKind.Past -> "2-я форма, прошедшее время"
        FormKind.Participle -> "3-я форма, «сделанный»"
        FormKind.Ing -> "форма с -ing, «делающий»"
        FormKind.S -> "форма с -s, после he, she, it"
    }

    override val restLegend = "всё остальное: кого, что, где, когда"
    override val bracketsLegend = "слово, которого по-русски не говорят. По-английски оно есть, поэтому в мысли его слышно"
    override val longLegend = "так в мысли звучит being: действие идёт"
}

private fun ruWhenSaid(tense: Tense): String? {
    val voice = tense.voice ?: return "Будущее, на которое смотрят из прошлого: «я знал, что он будет мыть машину». Так " +
        "пересказывают, что кто-то говорил, знал или думал о будущем."
    val aspect = tense.aspect ?: return null
    val time = tense.time ?: return null
    return if (voice == Voice.Active) {
        when (aspect) {
            Aspect.Simple -> when (time) {
                Time.Present -> "Простое настоящее время. Так говорят о том, что бывает всегда или регулярно: каждый день, " +
                    "обычно, по пятницам, — и о простых фактах: «я живу в Москве»."
                Time.Past -> "Простое прошедшее время. Так говорят о том, что случилось в прошлом и закончилось: вчера, в " +
                    "прошлом году, однажды."
                Time.Future -> "Простое будущее время. Так говорят о том, что случится потом: завтра, на следующей неделе, " +
                    "когда-нибудь."
            }
            Aspect.Continuous -> when (time) {
                Time.Present -> "Действие идёт прямо сейчас, оно в процессе: «я сейчас мою машину». Подсказки: now — " +
                    "сейчас, at the moment — в этот момент."
                Time.Past -> "Действие шло в какой-то момент или какое-то время в прошлом: «вчера весь день я мыл машину», " +
                    "«когда ты позвонил, я мыл машину»."
                Time.Future -> "Действие будет идти в какой-то момент или какое-то время в будущем: «завтра с трёх до семи " +
                    "я буду мыть машину»."
            }
            Aspect.Perfect -> when (time) {
                Time.Present -> "Действие уже сделано, и сейчас важен результат: «я уже вымыл машину — она чистая». " +
                    "Подсказки: already — уже, just — только что, never — никогда, ever — когда-нибудь."
                Time.Past -> "Действие было уже сделано к какому-то моменту в прошлом: «к пяти вчера я уже вымыл машину»."
                Time.Future -> "Действие будет уже сделано к какому-то моменту в будущем: «к пяти завтра я уже вымою машину»."
            }
            Aspect.PerfectContinuous -> when (time) {
                Time.Present -> "Действие началось раньше и длится до сих пор, и важно, как долго: «я мою машину уже два " +
                    "часа». Подсказки: for two hours — целых два часа, since morning — с утра."
                Time.Past -> "Действие длилось какое-то время до момента в прошлом: «я мыл машину уже два часа, когда ты " +
                    "пришёл»."
                Time.Future -> "Действие будет длиться какое-то время к моменту в будущем: «к пяти я буду мыть машину уже " +
                    "два часа»."
            }
        }
    } else {
        when (aspect) {
            Aspect.Simple -> when (time) {
                Time.Present -> "Так говорят, что с чем-то это делают регулярно или всегда: «машину моют каждый день». Кто " +
                    "делает, не важно или не известно."
                Time.Past -> "Так говорят, что с чем-то это сделали в прошлом: «машину вымыли вчера». Кто сделал, не важно " +
                    "или не известно."
                Time.Future -> "Так говорят, что с чем-то это сделают в будущем: «машину вымоют завтра»."
            }
            Aspect.Continuous -> when (time) {
                Time.Present -> "Так говорят, что с чем-то это делают прямо сейчас: «машину сейчас моют»."
                Time.Past -> "Так говорят, что с чем-то это делали в какой-то момент или какое-то время в прошлом: «машину " +
                    "мыли весь день»."
                Time.Future -> "Так говорят, что с чем-то это будут делать какое-то время в будущем: «завтра машину будут " +
                    "мыть весь день». Пассивом так почти не говорят, поэтому говорят «они будут мыть»."
            }
            Aspect.Perfect -> when (time) {
                Time.Present -> "Так говорят, что с чем-то это уже сделано и важен результат: «машина уже вымыта»."
                Time.Past -> "Так говорят, что с чем-то это было уже сделано к моменту в прошлом: «к пяти вчера машина уже " +
                    "была вымыта»."
                Time.Future -> "Так говорят, что с чем-то это будет уже сделано к моменту в будущем: «к пяти завтра машина " +
                    "уже будет вымыта»."
            }
            Aspect.PerfectContinuous -> "Так говорили бы, что с чем-то это делают какое-то время до какого-то момента, но " +
                "так почти не говорят."
        }
    }
}

private fun ruKindAbout(kind: Kind): String = when (kind) {
    Kind.Statement -> "Это утверждение: обычная фраза, не вопрос и без «не»."
    Kind.Negative -> "Это отрицание: фраза с «не»."
    Kind.Question -> "Это вопрос."
    Kind.Reported -> "Это косвенный вопрос: пересказ чужого вопроса, «он спросил, будем ли мы…»."
}

private fun ruVoiceAbout(tense: Tense): String? = when (tense.voice) {
    Voice.Active -> "«Актив» значит, что действие делает сам тот, о ком говорим."
    Voice.Passive -> "«Пассив» значит, что действие делают над ним, а не он сам."
    null -> null
}

private fun ruNames(tense: Tense): String? {
    if (tense.voice == null) return "В учебниках это называют Future in the Past."
    val aspect = tense.aspect ?: return null
    val (number, what) = when (aspect) {
        Aspect.Simple -> 1 to "просто факт"
        Aspect.Continuous -> 2 to "действие идёт"
        Aspect.Perfect -> 3 to "действие уже сделано"
        Aspect.PerfectContinuous -> 4 to "действие длится до какого-то момента"
    }
    return "«Тип $number» — номер в курсе Драгункина: $what. В учебниках это время называют ${tense.englishName()}."
}

// The rules.

private fun ruKindRules(kind: Kind, tense: Tense, verb: String): List<String> = when (kind) {
    Kind.Statement -> listOf(
        "Порядок слов строгий: кто, потом служебное слово, если оно есть, потом глагол, потом всё остальное. По-русски " +
            "слова можно переставлять, по-английски нельзя.",
    )
    Kind.Negative -> if (isBeOfType1(tense, verb)) {
        listOf("У be своё not, сразу после него: is not — «есть не», was not — «был не». do и did здесь не нужны.")
    } else {
        listOf(
            "not («не») ставят сразу после первого служебного слова: will not wash — «буду не мыть». В мысли «не» стоит " +
                "там же, хотя по-русски так не говорят.",
            "Обычно not сливается со служебным словом: did not — didn't, will not — won't, have not — haven't.",
        )
    }
    Kind.Question -> buildList {
        val simple = tense.voice == Voice.Active && tense.aspect == Aspect.Simple && tense.time != Time.Future
        when {
            isBeOfType1(tense, verb) -> add("Чтобы спросить, be ставят в начало, перед «кто»: You are… — Are you…? В мысли — «Есть ты…?»")
            tense.voice == null -> add(
                "Спрашивает главная часть, в своём времени: Did you think that he would…? — «[Сделал] ты думать, что он " +
                    "будет…?» Часть с would остаётся как в обычной фразе.",
            )
            simple -> add(
                "В обычной фразе тут нет служебного слова, поэтому для вопроса берут " +
                    (if (tense.time == Time.Past) "did" else "do или does") + " и ставят в самое начало, перед «кто»: " +
                    (if (tense.time == Time.Past) "Did you wash…?" else "Do you wash…?"),
            )
            else -> add("Чтобы спросить, первое служебное слово ставят в самое начало, перед «кто»: You will wash… — Will you wash…?")
        }
        add("В мысли порядок тот же, английский, и без «ли»: «Будешь ты мыть…?», «[Сделал] ты мыть…?»")
        add("Если спрашивают когда, почему или где, слово when, why или where ставят перед всем: When did you wash it?")
    }
    Kind.Reported -> listOf(
        "Это не сам вопрос, а пересказ: «он спросил, будем ли мы…». После asked whether слова идут как в обычной фразе, " +
            "и знака вопроса нет.",
        "whether — «ли». В мысли оно стоит в начале своей части: «Он спросил, ли мы будем…».",
    )
}

private fun ruTenseRules(tense: Tense): List<String> {
    val voice = tense.voice ?: return listOf(
        "После said (сказал), knew (знал), thought (думал) вместо will ставят would, а глагол — в 1-й форме.",
        "В мысли would — «будет», как will: «Я знал, что он будет мыть».",
        "Нет that — нет и «что»: I knew you would… — «Я знал, ты будешь…».",
    )
    val aspect = tense.aspect ?: return emptyList()
    val time = tense.time ?: return emptyList()
    if (voice == Voice.Passive) return ruPassiveRules(aspect, time)
    return when (aspect) {
        Aspect.Simple -> when (time) {
            Time.Present -> listOf(
                "В обычной фразе служебного слова нет: кто, потом глагол в 1-й форме. I wash — «я мою».",
                "После he, she, it (он, она, оно) и вообще после одного человека или предмета к глаголу добавляется -s: " +
                    "he washes — «он моет».",
                "В отрицании и вопросе нужно служебное слово do, а после he, she, it — does. -s переходит в does, и глагол " +
                    "снова без -s: he doesn't wash, does he wash?",
                "do и does по-русски не говорят, поэтому в мысли они в квадратных скобках: «Он [делает] не мыть».",
            )
            Time.Past -> listOf(
                "В обычной фразе служебного слова нет: кто, потом глагол во 2-й форме. I washed — «я вымыл».",
                "2-я форма у обычных глаголов — это глагол и -ed: wash — washed. У неправильных она своя, её запоминают: " +
                    "go — went, see — saw.",
                "В отрицании и вопросе нужно служебное слово did, а глагол возвращается в 1-ю форму, как в словаре: " +
                    "I didn't wash, did you wash?",
                "did по-русски не говорят, поэтому в мысли он в квадратных скобках: «Я [сделал] не мыть».",
            )
            Time.Future -> listOf(
                "will, потом глагол в 1-й форме, одинаково для всех: I will wash, she will wash.",
                "В мысли will — всегда «буду» (будешь, будет), а глагол после него — «мыть», а не «вымыть»: «я буду мыть».",
                "won't — это will not: «буду не».",
            )
        }
        Aspect.Continuous -> listOf(
            "Нужны обе части: be в нужном времени и глагол с -ing. Без be нельзя: не I washing, а I am washing.",
            when (time) {
                Time.Present -> "be в настоящем: am после I, is после he, she, it, are после you, we, they. В мысли — " +
                    "«являюсь»: «я являюсь моющим»."
                Time.Past -> "be в прошедшем: was после I, he, she, it, were после you, we, they. В мысли — «был»: «я был моющим»."
                Time.Future -> "will be — одинаково для всех. В мысли — «буду быть»: «я буду быть моющим»."
            },
            "Глагол с -ing в мысли — «моющим», «делающим»: тот, кто как раз делает.",
        )
        Aspect.Perfect -> listOf(
            "have в нужном времени и глагол в 3-й форме. У обычных глаголов 3-я форма такая же, как 2-я: washed. У " +
                "неправильных — своя: written, seen.",
            when (time) {
                Time.Present -> "have после I, you, we, they, has после he, she, it."
                Time.Past -> "had — одинаково для всех. В мысли — «имел»."
                Time.Future -> "will have — одинаково для всех. В мысли — «буду иметь»."
            },
            "В мысли have — «имею», а 3-я форма — «вымытой», «сделанным»: «я имею вымытой мою машину». Так слышно, что " +
                "результат уже есть. Если после глагола нет «кого, что», то на -ым: «имею уступленным».",
        )
        Aspect.PerfectContinuous -> listOf(
            "have been и глагол с -ing. been в мысли — «быть»: «я имею быть моющим».",
            when (time) {
                Time.Present -> "have been после I, you, we, they, has been после he, she, it."
                Time.Past -> "had been — одинаково для всех. В мысли — «имел быть»."
                Time.Future -> "will have been — одинаково для всех. В мысли — «буду иметь быть»."
            },
            "Отрицание здесь звучит неестественно, поэтому обычно говорят только утверждение и вопрос.",
        )
    }
}

private fun ruPassiveRules(aspect: Aspect, time: Time): List<String> {
    val general = listOf(
        "Пассив: be в нужном времени и глагол в 3-й форме. My car is washed — «Моя машина есть вымыта».",
        "Кто сделал, если хотят сказать, — после by, в мысли «от»: by the police — «от [известной] полиции».",
    )
    return when (aspect) {
        Aspect.Simple -> general + when (time) {
            Time.Present -> "am, is или are и 3-я форма. В мысли — «есть вымыта»."
            Time.Past -> "was или were и 3-я форма. В мысли — «была вымыта»."
            Time.Future -> "will be и 3-я форма, одинаково для всех. В мысли — «будет быть вымыта»."
        }
        Aspect.Continuous -> if (time == Time.Future) {
            listOf(
                "will be being washed почти не говорят, поэтому говорят «они будут мыть»: they will be washing — «Они " +
                    "будут быть моющими мою машину».",
                "Это как актив типа 2: will be и глагол с -ing.",
            )
        } else {
            general + listOf(
                "being стоит между be и 3-й формой и показывает, что действие идёт. В мысли being — «долго» в кавычках.",
                if (time == Time.Present) "am, is или are, потом being и 3-я форма: «есть «долго» вымыта»."
                else "was или were, потом being и 3-я форма: «была «долго» вымыта».",
            )
        }
        Aspect.Perfect -> general + listOf(
            "been стоит между have и 3-й формой. В мысли have — «имеет», been — «быть»: «имеет быть вымыта».",
            when (time) {
                Time.Present -> "have been после I, you, we, they, has been после he, she, it."
                Time.Past -> "had been — одинаково для всех: «имела быть вымыта»."
                Time.Future -> "will have been — одинаково для всех: «будет иметь быть вымыта»."
            },
        )
        Aspect.PerfectContinuous -> general +
            ("have been being и 3-я форма почти не говорят: вместо этого говорят пассив типа 3 или актив типа 4.")
    }
}

private fun ruPhraseRules(phrase: Breakdown, english: String): List<String> = buildList {
    phrase.negativeWord?.let { word ->
        val ru = NegativesRu[word]?.let { " («$it»)" } ?: ""
        add(
            "Здесь «не» даёт слово $word$ru: отдельного not нет, и глагол стоит как в обычной фразе. По-английски в " +
                "одной фразе только одно отрицание, поэтому и в мысли одно: nothing — «ничто», never — «никогда».",
        )
    }
    phrase.modal?.let { modal ->
        add(
            "$modal («${RussianModals[modal] ?: modal}») — модальный глагол. Он стоит на месте служебного слова, после " +
                "него глагол в 1-й форме, а «не» и вопрос он делает сам, без do.",
        )
    }
    val adverbs = phrase.adverbs()
    if (adverbs.isNotEmpty()) {
        val named = adverbs.joinToString(", ") { word -> AdverbsRu[word]?.let { "$word («$it»)" } ?: word }
        add("$named стоит внутри, между служебным словом и глаголом, и в мысли там же: has already been — «имеет уже быть».")
    }
    if (hasArticle(english)) {
        add(
            "the — «[известный]», a и an — «[какой-то]». По-русски таких слов нет, поэтому в мысли они в квадратных " +
                "скобках. Произносить их надо всегда.",
        )
    }
}

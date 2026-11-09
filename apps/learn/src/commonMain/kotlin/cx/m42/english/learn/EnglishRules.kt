package cx.m42.english.learn

/** The cap's page in English: see [RuleTexts]. What it quotes of the thought stays Russian. */
internal object EnglishRules : RuleTexts() {
    override val phraseTitle = "This phrase"
    override val translation = "In Russian"
    override val thoughtAbout = "The same phrase in Russian words, but in the English order, word for word. Every English " +
        "word is heard, even one Russian does not say. Say the phrase this way, and all that is left is to put each word " +
        "into English."
    override val partsTitle = "Part by part"
    override val noBreakdown = "This phrase could not be split into parts: no form of its verb was found in it."
    override val formulaTitle = "Pattern"
    override val inEnglish = "In English"
    override val thoughtOnWash = "How the English think, shown on «мыть», wash"
    override val rulesTitle = "Keep in mind"
    override val glossaryTitle = "Words and signs"
    override val glossary = listOf(
        "Type 1" to "a plain fact: once, regularly or always",
        "Type 2" to "the action is going on, the process counts: now, all day",
        "Type 3" to "the action is already done, the result counts: already, by five",
        "Type 4" to "the action lasts for some time up to some moment: for two hours",
        "Active" to "the subject does the action itself: I wash my car",
        "Passive" to "the action is done to the subject: my car is washed",
        "Helper word" to "do, did, will, have, be and their forms. It means almost nothing by itself, but shows the tense " +
            "and the kind of phrase. Grammar books call it an auxiliary verb",
        "1st, 2nd, 3rd form" to "the three main forms of a verb: wash — washed — washed, go — went — gone. The 1st is as " +
            "in a dictionary, the 2nd is the past, the 3rd is «сделанный», done. Regular verbs have the same 2nd and 3rd " +
            "form, with -ed; irregular ones have their own, learned by heart",
    )
    override val who = "who"
    override val what = "what"
    override val and = "and"

    override fun whenSaid(tense: Tense): String? {
        val voice = tense.voice ?: return "The future seen from the past: I knew that he would wash my car. It retells " +
            "what someone said, knew or thought about the future."
        val aspect = tense.aspect ?: return null
        val time = tense.time ?: return null
        return if (voice == Voice.Active) {
            when (aspect) {
                Aspect.Simple -> when (time) {
                    Time.Present -> "The simple present. It is for what happens always or regularly — every day, usually, " +
                        "on Fridays — and for plain facts: I live in Moscow."
                    Time.Past -> "The simple past. It is for what happened in the past and is over: yesterday, last year, once."
                    Time.Future -> "The simple future. It is for what will happen later: tomorrow, next week, some day."
                }
                Aspect.Continuous -> when (time) {
                    Time.Present -> "The action is going on right now: I am washing my car now. Hints: now, at the moment."
                    Time.Past -> "The action was going on at some moment or for some time in the past: I was washing my " +
                        "car all day yesterday; I was washing my car when you called."
                    Time.Future -> "The action will be going on at some moment or for some time in the future: tomorrow " +
                        "from three to seven I will be washing my car."
                }
                Aspect.Perfect -> when (time) {
                    Time.Present -> "The action is already done, and what counts now is the result: I have already " +
                        "washed my car, it is clean. Hints: already, just, never, ever."
                    Time.Past -> "The action was already done by some moment in the past: by five yesterday I had washed my car."
                    Time.Future -> "The action will already be done by some moment in the future: by five tomorrow I " +
                        "will have washed my car."
                }
                Aspect.PerfectContinuous -> when (time) {
                    Time.Present -> "The action began earlier and is still going on, and what counts is how long: I " +
                        "have been washing my car for two hours. Hints: for two hours, since morning."
                    Time.Past -> "The action had been going on for some time up to a moment in the past: I had been " +
                        "washing my car for two hours when you came."
                    Time.Future -> "The action will have been going on for some time by a moment in the future: by five " +
                        "I will have been washing my car for two hours."
                }
            }
        } else {
            when (aspect) {
                Aspect.Simple -> when (time) {
                    Time.Present -> "It says that something is done to the subject regularly or always: my car is " +
                        "washed every day. Who does it does not matter or is not known."
                    Time.Past -> "It says that something was done to the subject in the past: my car was washed " +
                        "yesterday. Who did it does not matter or is not known."
                    Time.Future -> "It says that something will be done to the subject: my car will be washed tomorrow."
                }
                Aspect.Continuous -> when (time) {
                    Time.Present -> "It says that something is being done to the subject right now: my car is being washed now."
                    Time.Past -> "It says that something was being done to the subject at some moment or for some time " +
                        "in the past: my car was being washed all day."
                    Time.Future -> "It says that something will be being done for some time in the future. The passive " +
                        "is hardly said here, so «they» is said instead: tomorrow they will be washing my car all day."
                }
                Aspect.Perfect -> when (time) {
                    Time.Present -> "It says that something has already been done to the subject, and the result " +
                        "counts: my car has already been washed."
                    Time.Past -> "It says that something had been done by a moment in the past: by five yesterday my " +
                        "car had been washed."
                    Time.Future -> "It says that something will have been done by a moment in the future: by five " +
                        "tomorrow my car will have been washed."
                }
                Aspect.PerfectContinuous -> "It would say that something has been being done to the subject for some " +
                    "time, but it is hardly ever said."
            }
        }
    }

    override fun kindAbout(kind: Kind): String = when (kind) {
        Kind.Statement -> "This is a statement: an ordinary phrase, not a question and with no not."
        Kind.Negative -> "This is a negative: a phrase with not."
        Kind.Question -> "This is a question."
        Kind.Reported -> "This is a reported question: someone's question retold, he asked whether we would…"
    }

    override fun voiceAbout(tense: Tense): String? = when (tense.voice) {
        Voice.Active -> "«Active» means the subject does the action itself."
        Voice.Passive -> "«Passive» means the action is done to the subject, not by it."
        null -> null
    }

    override fun names(tense: Tense): String? {
        if (tense.voice == null) return "Grammar books call it Future in the Past."
        val aspect = tense.aspect ?: return null
        val (number, meaning) = when (aspect) {
            Aspect.Simple -> 1 to "a plain fact"
            Aspect.Continuous -> 2 to "the action is going on"
            Aspect.Perfect -> 3 to "the action is already done"
            Aspect.PerfectContinuous -> 4 to "the action lasts up to some moment"
        }
        return "«Type $number» is its number in Dragunkin's course: $meaning. Grammar books call this tense ${tense.englishName()}."
    }

    override fun rules(phrase: Breakdown, tense: Tense, verb: String, english: String): List<String> =
        kindRules(phrase.kind, tense, verb) + tenseRules(tense) + phraseRules(phrase, english)

    override val mainPart = "the main part: who said, knew, thought or asked. What will be comes after it."
    override val frontPart = "when, where or how: such words go at the start or at the end of the phrase."
    override val restPart = "the rest: whom, what, where, when."

    override fun questionWordPart(several: Boolean, russian: String?): String {
        val gloss = russian?.let { " «$it»" } ?: ""
        return if (several) "question words$gloss. They go at the very start." else "a question word$gloss. It goes at the very start."
    }

    override fun subjectPart(passive: Boolean, russian: String?): String {
        val gloss = russian?.let { ": «$it»" } ?: ""
        return if (passive) "who or what it is done to$gloss." else "who does it$gloss."
    }

    override fun auxAbout(aux: Aux): String = when (aux.word) {
        "do" -> "the helper word of the present, after I, you, we, they"
        "does" -> "the helper word of the present, after he, she, it"
        "did" -> "the helper word of the past"
        "will" -> "the helper word of the future"
        "shall" -> "the helper word of the future, after I and we"
        "would" -> "will, when the future is seen from the past"
        "am" -> "be in the present, after I"
        "is" -> "be in the present, after he, she, it"
        "are" -> "be in the present, after you, we, they"
        "was" -> "be in the past, after I, he, she, it"
        "were" -> "be in the past, after you, we, they"
        "be" -> "be in its 1st form"
        "been" -> "be in its 3rd form"
        "being" -> "be with -ing: the action is going on"
        "have" -> if (aux.base) "have in its 1st form" else "have, after I, you, we, they"
        "has" -> "have, after he, she, it"
        "had" -> "have in the past"
        else -> "a modal verb, in the place of the helper word"
    }

    override fun inThought(thought: String): String =
        if (thought.startsWith("[")) "Russian does not say it, so in the thought it is in square brackets: «$thought»."
        else "In the thought: ${quoted(thought)}."

    override fun quoted(thought: String): String = if (thought.startsWith("«")) "$thought in quotes" else "«$thought»"

    override fun writtenOut(full: String) = "this is $full, "
    override val unknownAux = "a helper word."
    override fun severalAux(each: String) = "helper words. In the thought: $each."
    override val bareNot = "«не». It goes straight after the first helper word, and «не» stands there in the thought too."

    override fun notWith(full: String, about: String?, thought: String?): String =
        if (about == null) "this is $full: «не»." else "this is $full: $about, and not, «не». In the thought: «$thought не»."

    override fun adverbPart(russian: String?): String =
        (russian?.let { "«$it»" } ?: "an adverb") + ". Such words go between the helper word and the verb, in the thought too."

    override fun verbPart(meaning: String, form: FormKind?, reason: Pair<Reason, String>?): String {
        val shape = when (form) {
            FormKind.Base -> " in its 1st form, as in a dictionary"
            FormKind.Past -> " in its 2nd form: the past"
            FormKind.Participle -> " in its 3rd form: «сделанный», done"
            FormKind.Ing -> " with -ing: «делающий», doing"
            FormKind.S -> " with -s: so it goes after he, she, it"
            null -> ""
        }
        val why = reason?.let { (cause, word) ->
            when (cause) {
                Reason.BaseAfterHelper -> " After $word the verb is always in its 1st form."
                Reason.ThirdAfterHave -> " After $word the verb is in its 3rd form."
                Reason.ThirdAfterBe -> " After $word the verb is in its 3rd form: that makes the passive."
                Reason.IngAfterBe -> " After $word the verb takes -ing."
            }
        } ?: ""
        return "the verb «$meaning»$shape.$why"
    }

    override fun subjectLegend(passive: Boolean) =
        if (passive) "what it is done to: my car, the letter" else "the one who does it: I, you, she, my brother"

    override fun formName(kind: FormKind) = when (kind) {
        FormKind.Base -> "1st form, as in a dictionary"
        FormKind.Past -> "2nd form, the past"
        FormKind.Participle -> "3rd form, «сделанный», done"
        FormKind.Ing -> "the -ing form, «делающий», doing"
        FormKind.S -> "the -s form, after he, she, it"
    }

    override val restLegend = "the rest: whom, what, where, when"
    override val bracketsLegend = "a word Russian does not say. English has it, so the thought has it too"
    override val longLegend = "how the thought says being: the action is going on"

    private fun kindRules(kind: Kind, tense: Tense, verb: String): List<String> = when (kind) {
        Kind.Statement -> listOf(
            "The order of words is strict: who, then the helper word if there is one, then the verb, then the rest. " +
                "Russian can move words around, English cannot.",
        )
        Kind.Negative -> if (isBeOfType1(tense, verb)) {
            listOf("be takes its own not, straight after it: is not — «есть не», was not — «был не». No do or did is needed.")
        } else {
            listOf(
                "not («не») goes straight after the first helper word: will not wash — «буду не мыть». In the thought " +
                    "«не» stands there too, though Russian does not put it there.",
                "not usually merges with the helper word: did not — didn't, will not — won't, have not — haven't.",
            )
        }
        Kind.Question -> buildList {
            val simple = tense.voice == Voice.Active && tense.aspect == Aspect.Simple && tense.time != Time.Future
            when {
                isBeOfType1(tense, verb) -> add("To ask, be goes to the start, before who: You are… — Are you…? In the thought: «Есть ты…?»")
                tense.voice == null -> add(
                    "The main part asks, in its own tense: Did you think that he would…? — «[Сделал] ты думать, что он " +
                        "будет…?» The part with would stays as in a statement.",
                )
                simple -> add(
                    "A statement has no helper word here, so a question takes " +
                        (if (tense.time == Time.Past) "did" else "do or does") + " and puts it at the very start, before who: " +
                        (if (tense.time == Time.Past) "Did you wash…?" else "Do you wash…?"),
                )
                else -> add("To ask, the first helper word goes to the very start, before who: You will wash… — Will you wash…?")
            }
            add("The thought keeps the same English order, with no «ли»: «Будешь ты мыть…?», «[Сделал] ты мыть…?»")
            add("To ask when, why or where, the word when, why or where goes before everything: When did you wash it?")
        }
        Kind.Reported -> listOf(
            "It is not a question but a retelling: he asked whether we would… After asked whether the words go as in a " +
                "statement, with no question mark.",
            "whether is «ли». In the thought it stands at the start of its part: «Он спросил, ли мы будем…».",
        )
    }

    private fun tenseRules(tense: Tense): List<String> {
        val voice = tense.voice ?: return listOf(
            "After said, knew or thought, would stands where will would, and the verb is in its 1st form.",
            "In the thought would is «будет», as will is: «Я знал, что он будет мыть».",
            "No that, no «что»: I knew you would… — «Я знал, ты будешь…».",
        )
        val aspect = tense.aspect ?: return emptyList()
        val time = tense.time ?: return emptyList()
        if (voice == Voice.Passive) return passiveRules(aspect, time)
        return when (aspect) {
            Aspect.Simple -> when (time) {
                Time.Present -> listOf(
                    "A statement has no helper word: who, then the verb in its 1st form. I wash — «я мою».",
                    "After he, she, it, and after any one person or thing, the verb takes -s: he washes — «он моет».",
                    "A negative and a question need the helper word do, or does after he, she, it. The -s moves into " +
                        "does, and the verb is without it again: he doesn't wash, does he wash?",
                    "Russian does not say do and does, so in the thought they are in square brackets: «Он [делает] не мыть».",
                )
                Time.Past -> listOf(
                    "A statement has no helper word: who, then the verb in its 2nd form. I washed — «я вымыл».",
                    "For regular verbs the 2nd form is the verb with -ed: wash — washed. Irregular verbs have their own, " +
                        "learned by heart: go — went, see — saw.",
                    "A negative and a question need the helper word did, and the verb goes back to its 1st form, as in " +
                        "a dictionary: I didn't wash, did you wash?",
                    "Russian does not say did, so in the thought it is in square brackets: «Я [сделал] не мыть».",
                )
                Time.Future -> listOf(
                    "will, then the verb in its 1st form, the same for everyone: I will wash, she will wash.",
                    "In the thought will is always «буду» (будешь, будет), and the verb after it is «мыть», not " +
                        "«вымыть»: «я буду мыть».",
                    "won't is will not: «буду не».",
                )
            }
            Aspect.Continuous -> listOf(
                "Both parts are needed: be in the right tense and the verb with -ing. Without be it is wrong: not I " +
                    "washing but I am washing.",
                when (time) {
                    Time.Present -> "be in the present: am after I, is after he, she, it, are after you, we, they. In " +
                        "the thought «являюсь»: «я являюсь моющим»."
                    Time.Past -> "be in the past: was after I, he, she, it, were after you, we, they. In the thought " +
                        "«был»: «я был моющим»."
                    Time.Future -> "will be, the same for everyone. In the thought «буду быть»: «я буду быть моющим»."
                },
                "The verb with -ing is «моющим», «делающим» in the thought: the one who is doing it just then.",
            )
            Aspect.Perfect -> listOf(
                "have in the right tense and the verb in its 3rd form. For regular verbs the 3rd form is the same as " +
                    "the 2nd: washed. Irregular ones have their own: written, seen.",
                when (time) {
                    Time.Present -> "have after I, you, we, they, has after he, she, it."
                    Time.Past -> "had, the same for everyone. In the thought «имел»."
                    Time.Future -> "will have, the same for everyone. In the thought «буду иметь»."
                },
                "In the thought have is «имею», and the 3rd form is «вымытой», «сделанным»: «я имею вымытой мою " +
                    "машину». So you hear that the result is there. With no whom or what after the verb it ends in -ым: " +
                    "«имею уступленным».",
            )
            Aspect.PerfectContinuous -> listOf(
                "have been and the verb with -ing. been is «быть» in the thought: «я имею быть моющим».",
                when (time) {
                    Time.Present -> "have been after I, you, we, they, has been after he, she, it."
                    Time.Past -> "had been, the same for everyone. In the thought «имел быть»."
                    Time.Future -> "will have been, the same for everyone. In the thought «буду иметь быть»."
                },
                "A negative sounds unnatural here, so usually only a statement and a question are said.",
            )
        }
    }

    private fun passiveRules(aspect: Aspect, time: Time): List<String> {
        val general = listOf(
            "The passive: be in the right tense and the verb in its 3rd form. My car is washed — «Моя машина есть вымыта».",
            "Who did it, if it is said, comes after by, in the thought «от»: by the police — «от [известной] полиции».",
        )
        return when (aspect) {
            Aspect.Simple -> general + when (time) {
                Time.Present -> "am, is or are and the 3rd form. In the thought «есть вымыта»."
                Time.Past -> "was or were and the 3rd form. In the thought «была вымыта»."
                Time.Future -> "will be and the 3rd form, the same for everyone. In the thought «будет быть вымыта»."
            }
            Aspect.Continuous -> if (time == Time.Future) {
                listOf(
                    "will be being washed is hardly said, so «they» is said instead: they will be washing — «Они будут " +
                        "быть моющими мою машину».",
                    "It is like the active of type 2: will be and the verb with -ing.",
                )
            } else {
                general + listOf(
                    "being stands between be and the 3rd form and shows that the action is going on. In the thought " +
                        "being is «долго» in quotes.",
                    if (time == Time.Present) "am, is or are, then being and the 3rd form: «есть «долго» вымыта»."
                    else "was or were, then being and the 3rd form: «была «долго» вымыта».",
                )
            }
            Aspect.Perfect -> general + listOf(
                "been stands between have and the 3rd form. In the thought have is «имеет», been «быть»: «имеет быть вымыта».",
                when (time) {
                    Time.Present -> "have been after I, you, we, they, has been after he, she, it."
                    Time.Past -> "had been, the same for everyone: «имела быть вымыта»."
                    Time.Future -> "will have been, the same for everyone: «будет иметь быть вымыта»."
                },
            )
            Aspect.PerfectContinuous -> general +
                ("have been being and the 3rd form is hardly ever said: the passive of type 3 or the active of type 4 is said instead.")
        }
    }

    private fun phraseRules(phrase: Breakdown, english: String): List<String> = buildList {
        phrase.negativeWord?.let { word ->
            val ru = NegativesRu[word]?.let { " («$it»)" } ?: ""
            add(
                "Here the word $word$ru makes it negative: there is no separate not, and the verb stays as in a " +
                    "statement. English has only one negative in a phrase, so the thought has one too: nothing — «ничто», " +
                    "never — «никогда».",
            )
        }
        phrase.modal?.let { modal ->
            add(
                "$modal («${RussianModals[modal] ?: modal}») is a modal verb. It stands in the place of the helper word, " +
                    "the verb after it is in its 1st form, and it makes the negative and the question itself, with no do.",
            )
        }
        val adverbs = phrase.adverbs()
        if (adverbs.isNotEmpty()) {
            val named = adverbs.joinToString(", ") { word -> AdverbsRu[word]?.let { "$word («$it»)" } ?: word }
            add("$named stays inside, between the helper word and the verb, and in the thought too: has already been — «имеет уже быть».")
        }
        if (hasArticle(english)) {
            add(
                "the is «[известный]», a and an are «[какой-то]». Russian has no such words, so in the thought they are " +
                    "in square brackets. They are always said.",
            )
        }
    }
}

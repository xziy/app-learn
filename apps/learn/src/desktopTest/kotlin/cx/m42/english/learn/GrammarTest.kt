package cx.m42.english.learn

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The base the app carries, as it is on disk: the tests run in the module's folder. */
private val Copy = File("src/commonMain/composeResources/files/verbs")

private fun realVerbs(): List<Verb> =
    Copy.listFiles().orEmpty().filter { it.isDirectory }.flatMap { theme ->
        theme.listFiles().orEmpty().filter { it.extension == "json" && it.name != "index.json" }.map { parseVerb(it.readText()) }
    }

private fun real(theme: String, word: String): Verb = parseVerb(File(Copy, "$theme/$word.json").readText())

private fun Verb.tense(voice: Voice?, aspect: Aspect?, time: Time?): Tense =
    tenses.first { it.voice == voice && it.aspect == aspect && it.time == time }

/** The parts of a phrase with a role, as the page lists them. */
private fun Breakdown.parts(): List<Pair<String, Role>> = chunks.mapNotNull { chunk -> chunk.role?.let { chunk.text.trim() to it } }

private fun Formula.englishText(subject: String = "S"): String = english.joinToString(" + ") { it.shown(subject) }

private fun Formula.thoughtText(): String = thought.joinToString(" + ") { it.shown("кто") }

private fun Slot.shown(subject: String): String = when (this) {
    is Slot.Word -> text
    Slot.Subject -> subject
    is Slot.Form -> kinds.joinToString(" / ") { it.symbol() }
}

class GrammarTest {
    private val wash = Verb(
        word = "wash",
        meaning = "мыть",
        tenses = emptyList(),
        forms = mapOf(
            FormKind.Base to listOf(WordForm("wash", "")),
            FormKind.Past to listOf(WordForm("washed", "")),
            FormKind.Participle to listOf(WordForm("washed", "")),
            FormKind.Ing to listOf(WordForm("washing", "")),
            FormKind.S to listOf(WordForm("washes", "")),
        ),
    )

    private fun tense(voice: Voice?, aspect: Aspect?, time: Time?) = Tense(voice, aspect, time)

    private val pastSimple = tense(Voice.Active, Aspect.Simple, Time.Past)

    @Test
    fun aQuestionHasItsAuxiliaryInFrontAndItsSubjectAfterIt() {
        val phrase = parsePhrase("Did you wash your hands?", wash, pastSimple)
        assertEquals(Kind.Question, phrase.kind)
        assertEquals(listOf("Did" to Role.Auxiliary, "you" to Role.Subject, "wash" to Role.Verb), phrase.parts())
        assertEquals(FormKind.Base, phrase.form)
        assertEquals("Did you wash your hands?", phrase.pieces.joinToString("") { it.text }, "the pieces give the phrase back")
    }

    @Test
    fun aNegativeHasItsNotAfterTheFirstAuxiliary() {
        val phrase = parsePhrase("He didn't wash these dishes yesterday.", wash, pastSimple)
        assertEquals(Kind.Negative, phrase.kind)
        assertEquals(listOf("He" to Role.Subject, "didn't" to Role.Not, "wash" to Role.Verb), phrase.parts())
        assertEquals("did not", spelledOut("didn't"))
    }

    @Test
    fun aStatementHasItsSubjectWithoutTheTimeBeforeIt() {
        val phrase = parsePhrase("Yesterday I washed my car.", wash, pastSimple)
        assertEquals(Kind.Statement, phrase.kind)
        assertEquals(listOf("I" to Role.Subject, "washed" to Role.Verb), phrase.parts())
        assertEquals(FormKind.Past, phrase.form)

        val passive = parsePhrase("By five tomorrow my car will have been washed.", wash, tense(Voice.Passive, Aspect.Perfect, Time.Future))
        assertEquals(listOf("my car" to Role.Subject, "will have been" to Role.Auxiliary, "washed." to Role.Verb), passive.parts())
        assertEquals(FormKind.Participle, passive.form, "the same word as the past, and the tense tells it apart")

        val every = parsePhrase("Every summer people wash their cars.", wash, tense(Voice.Active, Aspect.Simple, Time.Present))
        assertEquals("people" to Role.Subject, every.parts().first())
    }

    @Test
    fun anAdverbStaysInsideTheGroupAndTheSubjectBetween() {
        val phrase = parsePhrase("Has the car already been washed?", wash, tense(Voice.Passive, Aspect.Perfect, Time.Present))
        assertEquals(
            listOf(
                "Has" to Role.Auxiliary,
                "the car" to Role.Subject,
                "already" to Role.Adverb,
                "been" to Role.Auxiliary,
                "washed?" to Role.Verb,
            ),
            phrase.parts(),
        )
    }

    @Test
    fun aShortAuxiliaryComesOffItsPronoun() {
        val phrase = parsePhrase("I'll wash it.", wash, tense(Voice.Active, Aspect.Simple, Time.Future))
        assertEquals(listOf("I" to Role.Subject, "'ll" to Role.Auxiliary, "wash" to Role.Verb), phrase.parts())
        assertEquals("I'll wash it.", phrase.pieces.joinToString("") { it.text })
        assertEquals("will", spelledOut("'ll"))
    }

    @Test
    fun aNegativeWordIsANegativeWithNoNot() {
        val phrase = parsePhrase("Nothing was washed yesterday.", wash, tense(Voice.Passive, Aspect.Simple, Time.Past))
        assertEquals(Kind.Negative, phrase.kind)
        assertEquals("nothing", phrase.negativeWord)

        val never = parsePhrase("He had never washed a car.", wash, tense(Voice.Active, Aspect.Perfect, Time.Past))
        assertEquals(Kind.Negative, never.kind)
        assertEquals("never", never.negativeWord)
    }

    @Test
    fun aNotInAnotherClauseIsNotTheVerbS() {
        val phrase = parsePhrase("I was being washed by someone I hadn't met.", wash, tense(Voice.Passive, Aspect.Continuous, Time.Past))
        assertEquals(Kind.Statement, phrase.kind)
    }

    @Test
    fun aQuestionToldIsReported() {
        val phrase = parsePhrase("He asked whether we would wash the car.", wash, tense(null, null, null))
        assertEquals(Kind.Reported, phrase.kind)
        assertEquals(listOf("we" to Role.Subject, "would" to Role.Auxiliary, "wash" to Role.Verb), phrase.parts())
    }

    @Test
    fun aModalIsTold() {
        val abide = real("general", "abide")
        val phrase = parsePhrase("My mother couldn't abide loud music.", abide, abide.tense(Voice.Active, Aspect.Simple, Time.Past))
        assertEquals(Kind.Negative, phrase.kind)
        assertEquals("could", phrase.modal)
    }

    @Test
    fun beIsItsOwnAuxiliary() {
        val be = real("yachting", "be")
        val present = be.tense(Voice.Active, Aspect.Simple, Time.Present)

        val no = parsePhrase("The sail is not ready.", be, present)
        assertEquals(Kind.Negative, no.kind)
        assertEquals(listOf("The sail" to Role.Subject, "is" to Role.Verb, "not" to Role.Not), no.parts())
        assertEquals("кто + V-s + not", present.formula(Kind.Negative, "be")!!.englishText("кто"))

        val asked = parsePhrase("Are you in the harbor?", be, present)
        assertEquals(Kind.Question, asked.kind)
        assertEquals(listOf("Are" to Role.Verb, "you" to Role.Subject), asked.parts())
        assertEquals("V-s + кто", present.formula(Kind.Question, "be")!!.englishText("кто"))
    }

    @Test
    fun theFormulasAreTheCourseS() {
        assertEquals("Did + S + V", pastSimple.formula(Kind.Question, "wash")!!.englishText())
        assertEquals("[Сделал] + кто + мыть", pastSimple.formula(Kind.Question, "wash")!!.thoughtText())
        assertEquals("S + didn't + V", pastSimple.formula(Kind.Negative, "wash")!!.englishText())
        assertEquals("S + V2", pastSimple.formula(Kind.Statement, "wash")!!.englishText())

        val present = tense(Voice.Active, Aspect.Simple, Time.Present)
        assertEquals("S + V / V-s", present.formula(Kind.Statement, "wash")!!.englishText())
        assertEquals("кто + [делаю/делает] не + мыть", present.formula(Kind.Negative, "wash")!!.thoughtText())

        val perfect = tense(Voice.Active, Aspect.Perfect, Time.Future)
        assertEquals("Will + S + have + V3", perfect.formula(Kind.Question, "wash")!!.englishText())
        assertEquals("кто + буду не + иметь + вымытой", perfect.formula(Kind.Negative, "wash")!!.thoughtText())

        val going = tense(Voice.Active, Aspect.PerfectContinuous, Time.Present)
        assertEquals("S + have/has + been + V-ing", going.formula(Kind.Statement, "wash")!!.englishText())

        val passive = tense(Voice.Passive, Aspect.Continuous, Time.Past)
        assertEquals("кто + была не + «долго» + вымыта", passive.formula(Kind.Negative, "wash")!!.thoughtText())

        // The one the course says in the active.
        val they = tense(Voice.Passive, Aspect.Continuous, Time.Future)
        assertEquals("they + will + be + V-ing", they.formula(Kind.Statement, "wash")!!.englishText())
        assertEquals("Future Continuous", they.englishName())
        assertEquals("Past Perfect Passive", tense(Voice.Passive, Aspect.Perfect, Time.Past).englishName())

        val future = tense(null, null, null)
        assertEquals("S + said/knew (that) + S + would + V", future.formula(Kind.Statement, "wash")!!.englishText())
        assertEquals("Future in the Past", future.englishName())
    }

    @Test
    fun everyPhraseOfTheBaseHasItsVerbFoundAndAFormula() {
        val verbs = realVerbs()
        assertTrue(verbs.size >= 50, "the base is there: ${Copy.absolutePath}")
        val lost = mutableListOf<String>()
        var phrases = 0
        for (verb in verbs) for (tense in verb.tenses) for (sentence in tense.sentences) {
            phrases++
            val phrase = parsePhrase(sentence.english, verb, tense)
            if (!phrase.hasVerb) lost += "${verb.word}: ${sentence.english}"
            assertNotNull(tense.formula(phrase.kind, verb.word), "${verb.word} ${tense.englishName()}")
            assertEquals(sentence.english.trim(), phrase.pieces.joinToString("") { it.text }, "the pieces give the phrase back")
            if (sentence.english.trim().endsWith("?")) assertEquals(Kind.Question, phrase.kind, sentence.english)
        }
        assertTrue(phrases > 3_000, "$phrases phrases")
        assertEquals(emptyList(), lost)
    }

    @Test
    fun eachPartIsSaidInPlainRussian() {
        fun parts(english: String, tense: Tense) =
            RussianRules.parts(parsePhrase(english, wash, tense), wash, tense).map { "${it.text} — ${it.about}" }

        assertEquals(
            listOf(
                "My car — кто или что, с ним это делают.",
                "has — have («иметь»), после he, she, it. В мысли — «имеет».",
                "already — «уже». Такие слова стоят между служебным словом и глаголом, и в мысли там же.",
                "been — be («быть») в 3-й форме. В мысли — «быть».",
                "washed — глагол «мыть» в 3-й форме: «сделанный». После been глагол в 3-й форме: так получается пассив.",
            ),
            parts("My car has already been washed.", tense(Voice.Passive, Aspect.Perfect, Time.Present)),
        )
        assertEquals(
            listOf(
                "I — кто делает: «я».",
                "'ll — это will, служебное слово будущего времени. В мысли — «буду».",
                "wash — глагол «мыть» в 1-й форме, как в словаре. После will глагол всегда в 1-й форме.",
                "it tomorrow — всё остальное: кого, что, где, когда.",
            ),
            parts("I'll wash it tomorrow.", tense(Voice.Active, Aspect.Simple, Time.Future)),
        )
        assertEquals(
            listOf(
                "Yesterday — когда, где или как: такие слова ставят в начало или в конец фразы.",
                "I — кто делает: «я».",
                "washed — глагол «мыть» во 2-й форме: это прошедшее время.",
                "my car — всё остальное: кого, что, где, когда.",
            ),
            parts("Yesterday I washed my car.", pastSimple),
        )
        assertEquals(
            "will have been — служебные слова. В мысли: will — «буду», have — «иметь», been — «быть».",
            parts("By five she will have been washing the car for two hours.", tense(Voice.Active, Aspect.PerfectContinuous, Time.Future))[2],
        )
    }

    @Test
    fun theEnglishPageSaysTheSameInEnglish() {
        val parts = EnglishRules.parts(parsePhrase("He didn't wash these dishes yesterday.", wash, pastSimple), wash, pastSimple)
        assertEquals(
            listOf(
                "He — who does it: «он».",
                "didn't — this is did not: the helper word of the past, and not, «не». In the thought: «[сделал] не».",
                "wash — the verb «мыть» in its 1st form, as in a dictionary. After did the verb is always in its 1st form.",
                "these dishes yesterday — the rest: whom, what, where, when.",
            ),
            parts.map { "${it.text} — ${it.about}" },
        )
    }

    @Test
    fun everyPartOfEveryPhraseOfTheBaseIsExplained() {
        val unexplained = mutableListOf<String>()
        for (texts in listOf(RussianRules, EnglishRules)) for (verb in realVerbs()) for (tense in verb.tenses) for (sentence in tense.sentences) {
            val phrase = parsePhrase(sentence.english, verb, tense)
            val formula = tense.formula(phrase.kind, verb.word)!!
            val parts = texts.parts(phrase, verb, tense)
            // Every word of the phrase is in a part, and only the punctuation is left out.
            assertEquals(
                sentence.english.filter { it.isLetterOrDigit() },
                parts.joinToString("") { it.text }.filter { it.isLetterOrDigit() },
                sentence.english,
            )
            val said = parts.map { "${it.text} — ${it.about}" } +
                texts.legend(formula, verb, tense).map { "${it.sign} — ${it.text}" } +
                texts.rules(phrase, tense, verb.word, sentence.english) + texts.about(tense, phrase.kind)
            said.filter { "null" in it || it.endsWith("— служебное слово.") || it.endsWith("— a helper word.") || "  " in it || it.endsWith(" — ") }
                .forEach { unexplained += "${verb.word}: ${sentence.english}: $it" }
        }
        assertEquals(emptyList(), unexplained)
    }

    @Test
    fun aQuestionOfTheBaseHasItsAuxiliaryOrItsVerbInFront() {
        val missed = mutableListOf<String>()
        for (verb in realVerbs()) for (tense in verb.tenses) for (sentence in tense.sentences) {
            val phrase = parsePhrase(sentence.english, verb, tense)
            if (phrase.kind != Kind.Question) continue
            val first = phrase.pieces.firstOrNull { it.role != null && it.role != Role.QuestionWord }?.role
            if (first != Role.Auxiliary && first != Role.Not && first != Role.Verb) missed += "${verb.word}: ${sentence.english}"
        }
        assertEquals(emptyList(), missed)
    }

    @Test
    fun noSubjectOfTheBaseHasATimeInIt() {
        val times = setOf("yesterday", "tomorrow", "now", "by", "noon", "five", "summer", "last", "next", "before", "after", "said", "knew")
        val wrong = mutableListOf<String>()
        for (verb in realVerbs()) for (tense in verb.tenses) for (sentence in tense.sentences) {
            val phrase = parsePhrase(sentence.english, verb, tense)
            val subject = phrase.chunks.firstOrNull { it.role == Role.Subject }?.text?.trim() ?: continue
            val words = subject.lowercase().split(' ').map { it.trim { c -> !c.isLetter() } }
            // A time that is the subject itself is no time caught in it: «Summer is spent on the yacht».
            if (words.any { it in times } && words.any { it !in times }) wrong += "$subject | ${sentence.english}"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun aPhraseWithoutTheVerbStillHasItsKind() {
        val phrase = parsePhrase("Is it so?", wash, pastSimple)
        assertEquals(Kind.Question, phrase.kind)
        assertTrue(!phrase.hasVerb)
        assertNull(phrase.form)
    }
}

class TimbreTest {
    private val phrases = List(40) { "Phrase number $it is said by someone." }

    @Test
    fun aPhraseHasTheSameTimbreEveryTime() {
        phrases.forEach { assertEquals(timbreFor(it, 6), timbreFor(it, 6)) }
    }

    @Test
    fun thePhrasesTakeTurnsAmongTheVoicesAndThePitches() {
        val timbres = phrases.map { timbreFor(it, 6) }
        assertTrue(timbres.map { it.voice }.distinct().size >= 5, "$timbres")
        assertTrue(timbres.map { it.pitch }.distinct().size == Pitches.size, "$timbres")
        assertTrue(timbres.zipWithNext().count { (a, b) -> a != b } >= phrases.size * 3 / 4, "most neighbours differ")
        assertTrue(timbres.all { it.voice in 0 until 6 })
    }

    @Test
    fun oneVoiceOrNoneStillChangesThePitch() {
        assertTrue(phrases.map { timbreFor(it, 1) }.all { it.voice == 0 })
        assertTrue(phrases.map { timbreFor(it, 0).pitch }.distinct().size > 1)
    }

    @Test
    fun espeakSpeaksSlowerInAVariantOfItsOwnAndTheTextComesLast() {
        val line = speechCommand("espeak-ng", "Did you wash your car?")
        assertEquals("espeak-ng", line[0])
        assertEquals("Did you wash your car?", line.last())
        val variant = line[line.indexOf("-v") + 1]
        assertTrue(variant.startsWith("en-us+") && variant.removePrefix("en-us+") in EspeakVariants, variant)
        assertTrue(line[line.indexOf("-s") + 1].toInt() < 145, "slower than it was")
        assertTrue(line[line.indexOf("-p") + 1].toInt() in 40..60)
    }

    @Test
    fun speechDispatcherAndSayAreSlowerToo() {
        val dispatcher = speechCommand("spd-say", "Hello there.")
        assertTrue(dispatcher[dispatcher.indexOf("-r") + 1].toInt() < 0)
        assertTrue(dispatcher[dispatcher.indexOf("-t") + 1] in DispatcherVoices)
        assertEquals("Hello there.", dispatcher.last())

        val say = speechCommand("say", "Hello there.") { listOf("Samantha", "Daniel") }
        assertTrue(say[say.indexOf("-v") + 1] in setOf("Samantha", "Daniel"))
        assertTrue(say[say.indexOf("-r") + 1].toInt() < 175)
        assertEquals("Hello there.", say.last())
    }
}

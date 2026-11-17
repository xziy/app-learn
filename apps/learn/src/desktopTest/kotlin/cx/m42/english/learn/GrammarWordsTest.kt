package cx.m42.english.learn

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.FakeAppRuntime
import cx.m42.superizer.testing.runAppTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun service(word: String, wordClass: String, thought: String, extra: String = "") =
    """{"word":"$word","class":"$wordClass","transcription":"[$word]","ru":"перевод $word","thought":"$thought","about":"О слове $word."$extra}"""

private val ServiceJson = """{"words":[
    ${service("the", "article", "[известный]", ""","variants":[{"transcription":"[ðи]","note":"перед гласным звуком"}]""")},
    ${service("a", "article", "[какой-то]")},
    ${service("did", "auxiliary", "[сделал]", ""","lemma":"do"""")},
    ${service("is", "auxiliary", "есть", ""","lemma":"be"""")},
    ${service("would", "auxiliary", "будет")},
    ${service("they", "pronoun", "они")},
    ${service("he", "pronoun", "он")},
    ${service("she", "pronoun", "она")},
    ${service("your", "possessive", "твой")},
    ${service("that", "determiner", "что", ""","also":["conjunction"]""")},
    ${service("to", "preposition", "к", ""","also":["particle"]""")}
]}"""

private fun irregular(verb: String, past: String, participle: String, ing: String, s: String) =
    """{"verb":"$verb","ru":"ru $verb","forms":{"base":[{"word":"$verb","transcription":"[$verb]"}],
    "past":[{"word":"$past","transcription":"[$past]"}],"participle":[{"word":"$participle","transcription":"[$participle]"}],
    "ing":[{"word":"$ing","transcription":"[$ing]"}],"s":[{"word":"$s","transcription":"[$s]"}]}}"""

private val IrregularJson = """{"verbs":[
    ${irregular("go", "went", "gone", "going", "goes")},
    ${irregular("say", "said", "said", "saying", "says")},
    ${irregular("light", "lit", "lit", "lighting", "lights")},
    ${irregular("wind", "wound", "wound", "winding", "winds")},
    ${irregular("be", "was", "been", "being", "is")}
]}"""

private val Grammar = GrammarWords(parseServiceWords(ServiceJson), parseIrregularVerbs(IrregularJson))

private fun verb(word: String, past: String, participle: String = past): Verb = Verb(
    word = word,
    meaning = "meaning of $word",
    tenses = emptyList(),
    forms = mapOf(
        FormKind.Base to listOf(WordForm(word, "[$word]")),
        FormKind.Past to listOf(WordForm(past, "[$past]")),
        FormKind.Participle to listOf(WordForm(participle, "[$participle]")),
    ),
)

private val Go = verb("go", "went", "gone")
private val Accept = verb("accept", "accepted")
private val Be = Verb("be", "быть", emptyList(), forms = mapOf(
    FormKind.Base to listOf(WordForm("be", "[би:]")),
    FormKind.Past to listOf(WordForm("was", "[wоз]")),
    FormKind.Participle to listOf(WordForm("been", "[би:н]")),
    FormKind.S to listOf(WordForm("is", "[из]")),
))
private val PastSimple = Tense(Voice.Active, Aspect.Simple, Time.Past)

private fun marks(text: String, verb: Verb, nouns: List<String> = emptyList(), tense: Tense = PastSimple): List<Mark> {
    val found = nounMarks(text, nouns.map { NounUse(it, null, "перевод", it) }) { setOf(it) }
    return phraseMarks(text, verb, tense, found, Grammar)
}

private fun links(text: String, verb: Verb, nouns: List<String> = emptyList(), tense: Tense = PastSimple) =
    marks(text, verb, nouns, tense).map { text.substring(it.start, it.end) to it.use.key }

class GrammarWordsTest {
    @Test
    fun theTablesAreRead() {
        val the = Grammar.service("The")!!
        assertEquals(WordClass.Article, the.wordClass)
        assertEquals("[известный]", the.thought)
        assertEquals(listOf(SoundVariant("[ðи]", "перед гласным звуком")), the.variants)
        assertEquals(listOf(WordClass.Conjunction), Grammar.service("that")!!.also)
        assertEquals("do", Grammar.service("did")!!.lemma)
        assertEquals("go", Grammar.verbOf("went")!!.verb)
        assertEquals("go — went — gone", Grammar.verb("go")!!.threeForms)
        assertEquals(FormKind.Past, Grammar.verb("go")!!.formOf("Went"))
        assertTrue(parseServiceWords("""{"words":[{"word":"x","class":"nonsense"}]}""").isEmpty(), "a class the app does not know is left out")
    }

    @Test
    fun aRegularVerbHasNoStarAndTheAuxiliaryIsPartOfTheFormula() {
        val text = "Did they accept your entry?"
        assertEquals(
            listOf("Did" to "f:did", "they" to "f:they", "your" to "f:your", "entry" to "n:entry"),
            links(text, Accept, listOf("entry")),
        )
        val uses = marks(text, Accept, listOf("entry")).map { it.use }
        assertEquals(true, (uses[0] as ServiceLink).inFormula, "did builds the tense here")
        assertEquals(false, (uses[1] as ServiceLink).inFormula)
    }

    @Test
    fun anIrregularVerbOfTheCardIsALinkToItself() {
        assertEquals(
            listOf("the" to "f:the", "crew" to "n:crew", "went" to "v:go", "to" to "f:to", "the" to "f:the", "harbor" to "n:harbor"),
            links("Yesterday the crew went to the harbor.", Go, listOf("crew", "harbor")),
        )
        assertEquals(listOf("The" to "f:the", "sail" to "n:sail", "is" to "v:be"), links("The sail is ready.", Be, listOf("sail"), Tense(Voice.Active, Aspect.Simple, Time.Present)))
    }

    @Test
    fun otherIrregularVerbsAreFoundToo() {
        assertEquals(
            listOf("He" to "f:he", "said" to "v:say", "that" to "f:that", "she" to "f:she", "would" to "f:would", "go" to "v:go"),
            links("He said that she would go.", Go, tense = Tense()),
        )
        assertEquals(listOf("They" to "f:they", "lit" to "v:light", "the" to "f:the", "lamp" to "n:lamp"), links("They lit the lamp.", Accept, listOf("lamp")))
    }

    @Test
    fun aFormAfterAnArticleIsNotAVerbAndANounStaysANoun() {
        assertEquals(listOf("A" to "f:a", "breeze" to "n:breeze", "went" to "v:go"), links("A light breeze went down.", Go, listOf("breeze")))
        assertEquals(listOf("Wind" to "n:wind", "went" to "v:go"), links("Wind went down.", Go, listOf("wind")))
    }

    @Test
    fun withoutTheTablesThereAreTheNounsAlone() {
        val text = "Yesterday the crew went to the harbor."
        val nouns = nounMarks(text, listOf(NounUse("crew", null, "экипаж", "crew"))) { setOf(it) }
        assertEquals(nouns, phraseMarks(text, Go, PastSimple, nouns, GrammarWords.Empty))
    }

    @Test
    fun theStarStandsOverTheVerbAndServiceWordsAreDottedTillTheyAreKnown() {
        val text = "Yesterday the crew went to the harbor."
        val found = marks(text, Go, listOf("crew", "harbor"))
        val linked = linkedPhrase(text, found, known = emptySet()) {}
        assertEquals("Yesterday the crew went* to the harbor.", linked.text.text)
        assertEquals(listOf("the", "to", "the"), linked.dotted.map { linked.text.text.substring(it.first, it.last + 1) })
        fun underlined(word: String, of: Linked): Boolean {
            val at = of.text.text.indexOf(word) + (word.length - word.trimStart().length)
            val link = of.text.getLinkAnnotations(at, at + 1).single().item as LinkAnnotation.Clickable
            return link.styles?.style?.textDecoration == TextDecoration.Underline
        }
        assertTrue(underlined("went", linked))
        assertFalse(underlined(" the", linked), "a service word has dots, not a line")

        val known = linkedPhrase(text, found, known = setOf("f:the", "v:go")) {}
        assertEquals("Yesterday the crew went* to the harbor.", known.text.text, "the star stays when the verb is known")
        assertEquals(listOf("to"), known.dotted.map { known.text.text.substring(it.first, it.last + 1) })
        assertFalse(underlined("went", known))
    }

    @Test
    fun theTranscriptionHasTheStarAndTheDotsOverTheSameWords() {
        val sentence = Sentence(
            "ours", "thought", "Yesterday the crew went to the harbor.", "[й**э**стэдэй ðэ кру: wэнт ту ðэ х**а**:бэ]", "active.simple.past.0",
        )
        val linked = linkedSound(sentence, marks(sentence.english, Go, listOf("crew", "harbor")), emptySet(), androidx.compose.ui.graphics.Color.White) {}
        assertEquals("[йэстэдэй ðэ кру: wэнт* ту ðэ ха:бэ]", linked.text.text)
        assertEquals(listOf("ðэ", "ту", "ðэ"), linked.dotted.map { linked.text.text.substring(it.first, it.last + 1) })
    }

    @Test
    fun theDictionaryFindsThemByAnyFormAndByTheTranslation() {
        assertEquals(listOf("go"), irregularRows(Grammar, "went").map { it.verb })
        assertEquals(listOf("be", "go", "light", "say", "wind"), irregularRows(Grammar, "").map { it.verb })
        assertEquals(listOf("the"), serviceRows(Grammar, "известн").map { it.word })
        assertEquals(WordClass.Auxiliary, serviceRows(Grammar, "").first().wordClass, "the service verbs come first")
    }

    /**
     * The whole base as the app carries it, with both tables: every irregular verb of a card has its star in each of
     * its phrases where the verb is found, a regular one never has, and no two links overlap.
     */
    @Test
    fun theBaseWithTheTables() {
        val files = File("src/commonMain/composeResources/files")
        val service = files.resolve("lexicon/function.json")
        val irregular = files.resolve("lexicon/irregular.json")
        if (!service.isFile || !irregular.isFile) return
        val grammar = GrammarWords(parseServiceWords(service.readText()), parseIrregularVerbs(irregular.readText()))
        val lexicon = parseLexicon(files.resolve("lexicon/nouns.json").readText())
        var phrases = 0
        var stars = 0
        val missing = mutableListOf<String>()
        for (theme in files.resolve("verbs").listFiles().orEmpty().filter { it.isDirectory }) {
            for (file in theme.listFiles().orEmpty().filter { it.name != "index.json" }) {
                val verb = parseVerb(file.readText())
                val own = grammar.verb(verb.word)
                for (tense in verb.tenses) for (sentence in tense.sentences) {
                    phrases++
                    val nouns = nounMarks(sentence.english, sentence.nouns) { lexicon.spellings(it) }
                    val found = phraseMarks(sentence.english, verb, tense, nouns, grammar)
                    found.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start, "overlap in ${sentence.english}") }
                    val ofCard = found.count { it.use.key == verbKey(verb.word) }
                    val hasVerb = parsePhrase(sentence.english, verb, tense).hasVerb
                    if (own == null) {
                        assertEquals(0, ofCard, "a regular verb has no star: ${sentence.english}")
                    } else if (hasVerb && ofCard == 0) {
                        missing += "${verb.word}: ${sentence.english}"
                    }
                    val linked = linkedPhrase(sentence.english, found, emptySet()) {}
                    val starred = found.count { it.use is VerbLink }
                    stars += starred
                    assertEquals(sentence.english.length + starred, linked.text.length)
                    linkedSound(sentence, found, emptySet(), androidx.compose.ui.graphics.Color.White) {}
                }
            }
        }
        assertTrue(missing.isEmpty(), "irregular verbs of a card without a star: ${missing.take(10)}")
        println("D25: $phrases phrases, $stars stars")
    }
}

/** Phrases of `go` and `accept` with the tables, for what a tap does. Pages: 0 the card, 1 a cover, 2 the past, 3 a cover, 4 the present. */
private fun grammarBase(): VerbSource = object : VerbSource {
    private fun example(english: String, sound: String) =
        """{"ours":"ours $english","thought":"thought $english","english":"$english","transcription":"$sound"}"""

    private val verbs = mapOf(
        "go" to """{"verb":"go","meaning":"идти","forms":{"base":[{"word":"go","transcription":"[гоу]"}],
            "past":[{"word":"went","transcription":"[wэнт]"}],"participle":[{"word":"gone","transcription":"[гон]"}]},
            "active":{"simple":{"past":{"form":"went","examples":[${example("Yesterday the crew went to the harbor.", "[й**э**стэдэй ðэ кру: wэнт ту ðэ х**а**:бэ]")}]},
            "present":{"form":"go, goes","examples":[${example("They go to sea.", "[ðэй гоу ту си:]")}]}}},
            "nouns":[{"word":"crew","meaning":"экипаж","used_in":[{"phrase":"active.simple.past.0","form":"crew"}]}]}""",
        "accept" to """{"verb":"accept","meaning":"принимать","forms":{"base":[{"word":"accept","transcription":"[экс**э**пт]"}],
            "past":[{"word":"accepted","transcription":"[экс**э**птид]"}],"participle":[{"word":"accepted","transcription":"[экс**э**птид]"}]},
            "active":{"simple":{"past":{"form":"accepted","examples":[${example("Did they accept your entry?", "[дид ðэй экс**э**пт йо: **э**нтри]")}]}}}}""",
    )

    override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: всё подряд"))

    override suspend fun index(theme: String) = listOf("go", "accept")

    override suspend fun read(theme: String, word: String): String = verbs.getValue(word)

    override suspend fun serviceWords(): String = ServiceJson

    override suspend fun irregularVerbs(): String = IrregularJson
}

private object Quiet : Speech {
    override val available: Boolean = false

    override suspend fun speak(text: String) = Unit

    override fun stop() = Unit
}

@OptIn(ExperimentalTestApi::class)
class GrammarWordPagesTest {
    private fun app() = LearnApp(grammarBase(), Quiet, shuffle = { it })

    private fun at(place: String): (FakeAppRuntime) -> Unit = {
        it.storage.entries["place"] = place
        it.storage.entries["chosen"] = "go,accept"
    }

    private fun AppTestScope<Unit>.settled(tag: String) {
        ui.waitUntil(timeoutMillis = 5_000L) { runCatching { node(tag).assertExists() }.isSuccess }
        idle()
    }

    private fun AppTestScope<Unit>.tapWord(tag: String, word: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.first()
        val at = layout.layoutInput.text.text.indexOf(word)
        check(at >= 0) { "No «$word» in «${layout.layoutInput.text.text}»" }
        node(tag).performTouchInput { click(layout.getBoundingBox(at + word.length / 2).center) }
        idle()
    }

    /** A picture of the screen for the eye, in `build/screenshots`, as the host's tests leave theirs. */
    private fun AppTestScope<Unit>.shot(name: String) {
        idle()
        val out = File("build/screenshots").apply { mkdirs() }
        ImageIO.write(ui.onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    private fun AppTestScope<Unit>.openFeed() {
        settled("learn:go")
        click("learn:go")
        settled("learn:english")
    }

    @Test
    fun aTapOnAnIrregularVerbOpensItsFormsAndItCanBeKnown() = runAppTest(app(), prepare = at("go:2:2")) {
        openFeed()
        node("learn:english").assertTextEquals("Yesterday the crew went* to the harbor.")
        shot("d25-the-star-and-the-dots")
        tapWord("learn:english", "went")
        settled("learn:grammar-page")
        shot("d25-the-irregular-verb")
        node("learn:grammar-word").assertTextEquals("go*")
        node("learn:grammar-three-forms").assertTextEquals("go — went — gone")
        node("learn:grammar-here-text").assertTextEquals("went — прошедшее время")
        click("learn:grammar-know")
        idle()
        assertEquals("v:go", runtime.storage.entries["lex.known"])
        click("learn:grammar-back")
        assertAbsent("learn:grammar-page")
        settled("learn:english")
        node("learn:english").assertTextEquals("Yesterday the crew went* to the harbor.")
        assertEquals("go:2:2", runtime.storage.entries["place"], "the word is not a step forward")
    }

    @Test
    fun aTapOnAServiceWordOpensWhatItIsAndHowTheThoughtHasIt() = runAppTest(app(), prepare = at("go:2:2")) {
        openFeed()
        tapWord("learn:english", "the")
        settled("learn:grammar-page")
        node("learn:grammar-word").assertTextEquals("the")
        node("learn:grammar-class").assertTextEquals("Артикль")
        node("learn:grammar-thought-text").assertTextEquals("[известный]")
        assertAbsent("learn:grammar-rule")
    }

    @Test
    fun anAuxiliaryOpensTheRuleOfItsTenseAndBackIsOnItsPage() = runAppTest(app(), prepare = at("accept:2:2")) {
        openFeed()
        tapWord("learn:english", "Did")
        settled("learn:grammar-page")
        node("learn:grammar-class").assertTextEquals("Вспомогательный глагол")
        node("learn:grammar-here-text").assertExists()
        click("learn:grammar-rule")
        settled("learn:rule-page")
        click("learn:rule-back")
        settled("learn:grammar-page")
    }

    @Test
    fun theDictionaryHasThemInTheirOwnFilters() = runAppTest(app()) {
        settled("learn:dict-open")
        click("learn:dict-open")
        settled("learn:dictionary")
        click("learn:filter-irregular")
        settled("learn:dict-v:go")
        click("learn:dict-know-v:go")
        idle()
        assertEquals("v:go", runtime.storage.entries["lex.known"])
        click("learn:filter-service")
        settled("learn:dict-f:the")
        click("learn:dict-f:the")
        settled("learn:grammar-page")
        node("learn:grammar-word").assertTextEquals("the")
    }
}

package cx.m42.english.learn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.FakeAppRuntime
import cx.m42.superizer.testing.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun example(english: String, sound: String) =
    """{"ours":"ours $english","thought":"thought $english","english":"$english","transcription":"$sound"}"""

private fun verbJson(word: String, past: String, present: String, future: String, nouns: String) =
    """{"verb":"$word","meaning":"meaning of $word","active":{"simple":{
        "past":{"form":"f","examples":[$past]},"present":{"form":"f","examples":[$present]},
        "future":{"form":"f","examples":[$future]}}},"nouns":[$nouns]}"""

/**
 * Two verbs of three phrases each, so the pages of each go: 0 its card, 1 a cover, 2 the past, 3 a cover, 4 the
 * present, 5 a cover, 6 the future. `sheet` stands in `verb0` in two senses, `crew` in both verbs.
 */
private val Verbs = mapOf(
    "verb0" to verbJson(
        "verb0",
        example("The crew cut the sheets.", "[ðэ кру: кат ðэ ши:тс]"),
        example("She reads a sheet.", "[ши: ри:дз э ши:т]"),
        example("english verb0 future", "[и**ŋ**глиш вэб фь**ю**:чэ]"),
        """{"word":"crew","meaning":"экипаж","used_in":[{"phrase":"active.simple.past.0","form":"crew"}]},
           {"word":"sheet","meaning":"шкот","used_in":[{"phrase":"active.simple.past.0","form":"sheets"}]},
           {"word":"sheet","meaning":"лист","sense":"paper","used_in":[{"phrase":"active.simple.present.0","form":"sheet"}]}""",
    ),
    "verb1" to verbJson(
        "verb1",
        example("The new crew lost the life jackets.", "[ðэ нью: кру: лост ðэ лайф дж**æ**китс]"),
        example("english verb1 present", "[и**ŋ**глиш вэб пр**э**знт]"),
        example("english verb1 future", "[и**ŋ**глиш вэб фь**ю**:чэ]"),
        """{"word":"crew","meaning":"экипаж","used_in":[{"phrase":"active.simple.past.0","form":"crew"}]},
           {"word":"life jacket","meaning":"спасательный жилет","used_in":[{"phrase":"active.simple.past.0","form":"life jackets"}]}""",
    ),
)

private const val LEXICON = """{"spec":1,"words":[
 {"word":"crew","status":"filled","transcription":"[кру:]",
  "noun":{"countable":"yes","plural":[{"word":"crews","transcription":"[кру:з]"}],"number":"collective"},
  "senses":[{"ru":["экипаж","команда"],"en":"the people who work on a ship","synonyms":[{"word":"team","note":"не на судне"}],
    "collocations":["join the crew"]}],
  "notes":"The crew are — о людях.","in":{"general":["verb0","verb1"]},"forms":["crew"],"count":{"":2}},
 {"word":"life jacket","status":"draft","senses":[{"ru":["спасательный жилет"]}],
  "in":{"general":["verb1"]},"forms":["life jackets"],"count":{"":1}},
 {"word":"sheet","status":"filled","transcription":"[ши:т]",
  "noun":{"countable":"yes","plural":[{"word":"sheets","transcription":"[ши:тс]"}]},
  "senses":[{"ru":["шкот"],"themes":["general"],"en":"a rope that controls a sail"},
    {"id":"paper","ru":["лист"],"en":"a thin flat piece of paper","collocations":["a sheet of paper"]}],
  "family":[{"word":"sheet","class":"verb","ru":"покрывать"}],
  "in":{"general":["verb0"]},"forms":["sheets","sheet"],"count":{"":1,"paper":1}}
]}"""

private fun wordsBase(withLexicon: Boolean = true): VerbSource = object : VerbSource {
    override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: всё подряд"))

    override suspend fun index(theme: String) = listOf("verb0", "verb1")

    override suspend fun read(theme: String, word: String): String = Verbs.getValue(word)

    override suspend fun lexicon(): String? = if (withLexicon) LEXICON else null
}

private fun use(word: String, form: String = word, sense: String? = null) = NounUse(word, sense, "перевод", form)

class LexiconTest {
    private val lexicon = parseLexicon(LEXICON)

    @Test
    fun aVerbsPhrasesKnowWhereTheyAreAndWhichNounsTheyHave() {
        val verb = parseVerb(Verbs.getValue("verb0"))
        val (past, present) = verb.tenses.take(2).map { it.sentences.single() }
        assertEquals("active.simple.past.0", past.address)
        assertEquals(listOf("n:crew", "n:sheet"), past.nouns.map { it.key })
        assertEquals("sheets", past.nouns[1].form)
        assertEquals("active.simple.present.0", present.address)
        assertEquals(NounUse("sheet", "paper", "лист", "sheet"), present.nouns.single())
        assertEquals("n:sheet#paper", present.nouns.single().key)
        assertEquals(emptyList(), verb.tenses[2].sentences.single().nouns)
    }

    @Test
    fun aVerbWithoutNounsStillReads() {
        val verb = parseVerb(verbJson("x", example("a", "[э]"), example("b", "[би:]"), example("c", "[си:]"), "").replace(""","nouns":[]""", ""))
        assertTrue(verb.tenses.all { tense -> tense.sentences.all { it.nouns.isEmpty() } })
    }

    @Test
    fun theDictionaryHasItsWordsSensesAndWhereTheyStand() {
        val sheet = lexicon.entry("sheet")!!
        assertTrue(sheet.filled)
        assertEquals("[ши:т]", sheet.transcription)
        assertEquals(listOf(WordForm("sheets", "[ши:тс]")), sheet.plural)
        assertEquals(listOf(null, "paper"), sheet.senses.map { it.id })
        assertEquals(listOf("general"), sheet.senses[0].themes)
        assertEquals(listOf("a sheet of paper"), sheet.senses[1].collocations)
        assertEquals(mapOf(null to 1, "paper" to 1), sheet.counts)
        assertEquals(2, sheet.phrases)
        assertEquals(mapOf("general" to listOf("verb0")), sheet.places)
        assertEquals(listOf(Kin("sheet", "verb", "покрывать")), sheet.family)
        assertEquals(NounNumber.Collective, lexicon.entry("crew")!!.number)
        assertEquals(listOf(Related("team", "не на судне")), lexicon.entry("crew")!!.senses[0].synonyms)
        assertTrue(!lexicon.entry("life jacket")!!.filled)
    }

    @Test
    fun aKeyIsOfAWordInOneOfItsSenses() {
        assertEquals("n:sheet", wordKey("sheet", null))
        assertEquals("n:sheet#paper", wordKey("sheet", "paper"))
        assertEquals(listOf("лист"), lexicon.find("n:sheet#paper")!!.sense.ru)
        assertEquals("life jacket", lexicon.find("n:life jacket")!!.entry.word)
        assertNull(lexicon.find("n:sheet#bed"), "a sense the dictionary does not have")
        assertNull(lexicon.find("n:gone"))
    }

    @Test
    fun aDictionaryThatCannotBeReadIsEmptyAndABrokenWordCostsOnlyItself() {
        assertTrue(parseLexicon("[]").entries.isEmpty())
        val one = parseLexicon("""{"words":[{"word":"x"},{"word":"y","senses":[{"ru":["игрек"]}]},7]}""")
        assertEquals(listOf("y"), one.entries.map { it.word })
    }

    @Test
    fun searchFindsAWordByItsFormsItsPartsAndItsTranslationsTheClosestFirst() {
        fun found(query: String) = search(query, lexicon.all).map { it.key }
        assertEquals(lexicon.all.map { it.key }, found("  "), "nothing typed is everything")
        assertEquals(listOf("n:crew"), found("Crews"))
        assertEquals(listOf("n:life jacket"), found("jack"))
        assertEquals(listOf("n:sheet#paper"), found("лист"))
        assertEquals(listOf("n:life jacket"), found("ЖИЛЕТ"))
        assertEquals(listOf("n:crew"), found("экипаж"))
        // «ё» and «е» are one letter: an ordinary keyboard has no «ё».
        val ferry = Lexicon(listOf(Entry("ferry", listOf(Sense(null, listOf("паром", "лёгкое судно"))))))
        assertEquals(listOf("n:ferry"), search("легкое", ferry.all).map { it.key })
        // The word as typed before one that only begins with it.
        val two = Lexicon(listOf(Entry("sea", listOf(Sense(null, listOf("море")))), Entry("season", listOf(Sense(null, listOf("сезон"))))))
        assertEquals(listOf("n:sea", "n:season"), search("sea", two.all.reversed()).map { it.key })
    }

    @Test
    fun aNounIsFoundInItsPhraseInEachOfItsSpellingsAsWholeWords() {
        val text = "Will the new sailor be accepted by the other sailors?"
        val marks = nounMarks(text, listOf(use("sailor"))) { setOf("sailor", "sailors") }
        assertEquals(listOf("sailor", "sailors"), marks.map { text.substring(it.start, it.end) })

        val captain = "The captain's boat is in the boatyard."
        assertEquals(listOf("captain", "boat"), nounMarks(captain, listOf(use("captain"), use("boat"))) { setOf(it) }.map { captain.substring(it.start, it.end) })

        val jackets = "Life jackets and a jacket."
        val both = nounMarks(jackets, listOf(use("jacket"), use("life jacket", "Life jackets"))) { if (it == "jacket") setOf("jacket", "jackets") else setOf("life jacket", "life jackets") }
        assertEquals(listOf("Life jackets", "jacket"), both.map { jackets.substring(it.start, it.end) }, "the longer one wins where they overlap")
    }

    @Test
    fun theSameNounsAreTheWordsTheyTakeUpInTheTranscription() {
        val english = "The new crew lost the life jackets."
        val marks = nounMarks(english, listOf(use("crew"), use("life jacket", "life jackets"))) { setOf(it) + "${it}s" }
        val sounds = soundMarks(english, "[ðэ нью: кру: лост ðэ лайф дж**æ**китс]", marks)
        assertEquals(listOf(2..2 to "crew", 5..6 to "life jacket"), sounds.map { it.first to it.second.word })
        assertEquals(emptyList(), soundMarks(english, "[ðэ кру:]", marks), "another number of words links nothing")
    }

    @Test
    fun theDictionaryListsTheLatestSeenFirstAndKnowsWhatIsKnown() {
        val marks = WordMarks(seen = listOf("n:crew", "n:sheet", "n:gone", "n:sheet#paper"), known = setOf("n:sheet", "n:life jacket"))
        fun rows(filter: WordFilter, query: String = "") = dictionaryRows(lexicon, marks, filter, query).map { it.key }
        assertEquals(listOf("n:sheet#paper", "n:sheet", "n:crew"), rows(WordFilter.Seen))
        assertEquals(listOf("n:sheet#paper", "n:crew"), rows(WordFilter.Unknown))
        assertEquals(listOf("n:sheet", "n:life jacket"), rows(WordFilter.Known), "known without being seen comes after")
        assertEquals(listOf("n:crew", "n:life jacket", "n:sheet", "n:sheet#paper"), rows(WordFilter.All))
        assertEquals(listOf("n:sheet#paper"), rows(WordFilter.Seen, "лист"))
    }
}

@OptIn(ExperimentalTestApi::class)
class WordsTest {
    private fun app(withLexicon: Boolean = true) = LearnApp(wordsBase(withLexicon), SilentSpeech, shuffle = { it })

    private fun at(place: String): (FakeAppRuntime) -> Unit = {
        it.storage.entries["place"] = place
        it.storage.entries["chosen"] = "verb0,verb1"
    }

    private fun AppTestScope<Unit>.settled(tag: String) {
        ui.waitUntil(timeoutMillis = 5_000L) { runCatching { node(tag).assertExists() }.isSuccess }
        idle()
    }

    private fun AppTestScope<Unit>.openFeed() {
        settled("learn:go")
        click("learn:go")
    }

    /** Where in a text node a word of it is, from its own layout. */
    private fun AppTestScope<Unit>.wordAt(tag: String, word: String): Offset {
        val layouts = mutableListOf<TextLayoutResult>()
        node(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.first()
        val at = layout.layoutInput.text.text.indexOf(word)
        check(at >= 0) { "No «$word» in «${layout.layoutInput.text.text}»" }
        return layout.getBoundingBox(at + word.length / 2).center
    }

    private fun AppTestScope<Unit>.tapWord(tag: String, word: String) {
        val at = wordAt(tag, word)
        node(tag).performTouchInput { click(at) }
        idle()
    }

    @Test
    fun aTapOnANounOpensItsPageAndBackIsWhereItWas() = runAppTest(app(), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:english")
        tapWord("learn:english", "sheets")
        settled("learn:word-page")

        node("learn:word-title").assertTextEquals("sheet")
        node("learn:word-sound").assertTextEquals("[ши:т]")
        node("learn:sense-main-ru").assertTextEquals("шкот")
        node("learn:sense-paper-ru").assertTextEquals("лист")
        node("learn:word-plural").assertTextEquals("Мн. ч.: sheets [ши:тс]")

        click("learn:word-back")
        assertAbsent("learn:word-page")
        settled("learn:english")
        node("learn:english").assertTextEquals("The crew cut the sheets.")
        assertEquals("verb0:2:2", runtime.storage.entries["place"], "the word is not a step forward")
    }

    @Test
    fun aTapOnAMouseClickOnTheWordOfTheTranscriptionOpensItToo() = runAppTest(app(), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:transcription")
        val at = wordAt("learn:transcription", "кру:")
        node("learn:transcription").performMouseInput { click(at) }
        idle()
        settled("learn:word-page")
        node("learn:word-title").assertTextEquals("crew")
        node("learn:word-number").assertExists()
    }

    @Test
    fun aTapBesideTheWordsStillGoesForward() = runAppTest(app(), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:english")
        node("learn:feed").performTouchInput { click(percentOffset(0.5f, 0.75f)) }
        idle()
        settled("learn:tense-card")
        assertAbsent("learn:word-page")
    }

    @Test
    fun aSwipeThatStartsOnAWordIsASwipe() = runAppTest(app(), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:english")
        val text = node("learn:english").getUnclippedBoundsInRoot()
        val at = wordAt("learn:english", "crew")
        // Begun on the word itself, as the other swipes of the feed are, on the feed.
        node("learn:feed").performTouchInput {
            val start = Offset(text.left.toPx() + at.x, text.top.toPx() + at.y)
            swipe(start, Offset(start.x, 0f))
        }
        idle()
        assertAbsent("learn:word-page")
        assertEquals("verb0:3:0", runtime.storage.entries["place"])
    }

    @Test
    fun theNounsOfAnEnglishStepOnScreenAreSeenAndOnlyThen() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        assertNull(runtime.storage.entries["lex.seen"])
        node("learn:feed").performTouchInput { swipeLeft() }
        idle()
        settled("learn:thought")
        assertNull(runtime.storage.entries["lex.seen"])
        node("learn:feed").performTouchInput { swipeLeft() }
        idle()
        settled("learn:english")
        assertEquals("n:crew,n:sheet", runtime.storage.entries["lex.seen"])
    }

    @Test
    fun aWordSeenAgainComesToTheTop() = runAppTest(app(), prepare = {
        at("verb1:2:2")(it)
        it.storage.entries["lex.seen"] = "n:crew,n:sheet#paper"
    }) {
        openFeed()
        settled("learn:english")
        assertEquals("n:sheet#paper,n:crew,n:life jacket", runtime.storage.entries["lex.seen"])
    }

    @Test
    fun aSenseIsMarkedKnownOnItsPageAndKept() = runAppTest(app(), prepare = at("verb0:4:2")) {
        openFeed()
        settled("learn:english")
        tapWord("learn:english", "sheet")
        settled("learn:word-page")
        node("learn:sense-in-phrase").assertExists()
        node("learn:know-paper").assertIsOff()
        click("learn:know-paper")
        node("learn:know-paper").assertIsOn()
        node("learn:know-main").assertIsOff()
        assertEquals("n:sheet#paper", runtime.storage.entries["lex.known"])
        click("learn:know-paper")
        assertNull(runtime.storage.entries["lex.known"])
    }

    @Test
    fun aPhraseOfTheWordsPageOpensItsVerbOnItsEnglish() = runAppTest(app(), prepare = at("verb1:2:2")) {
        openFeed()
        settled("learn:english")
        tapWord("learn:english", "crew")
        settled("learn:word-page")
        node("learn:word-uses").assertTextEquals("Встречается в 2 фразах")
        val phrase = "learn:use-verb0-active.simple.past.0"
        ui.waitUntil(timeoutMillis = 5_000L) {
            runCatching { node("learn:word-list").performScrollToNode(hasTestTag(phrase)) }.isSuccess
        }
        click(phrase)
        assertAbsent("learn:word-page")
        settled("learn:english")
        node("learn:english").assertTextEquals("The crew cut the sheets.")
        node("learn:word").assertTextEquals("verb0")
        assertEquals("verb1:2:2", runtime.storage.entries["place"], "a second look moves no place")
    }

    @Test
    fun withoutADictionaryAWordStillHasItsPageWithWhatThePhraseSays() = runAppTest(app(withLexicon = false), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:english")
        tapWord("learn:english", "crew")
        settled("learn:word-page")
        node("learn:word-title").assertTextEquals("crew")
        node("learn:sense-main-ru").assertTextEquals("экипаж")
        assertAbsent("learn:word-uses")
    }

    @Test
    fun theDictionaryListsTheWordsSeenFindsThemAndMarksThem() = runAppTest(app(), prepare = {
        it.storage.entries["lex.seen"] = "n:crew,n:sheet,n:sheet#paper"
    }) {
        settled("learn:dict-open")
        click("learn:dict-open")
        settled("learn:dictionary")
        node("learn:dict-summary").assertTextEquals("Встречено 3 · знаю 0")
        node("learn:dict-n:sheet#paper").assertExists()
        node("learn:dict-n:crew").assertExists()
        assertAbsent("learn:dict-n:life jacket")

        type("learn:dict-search", "лист")
        node("learn:dict-n:sheet#paper").assertExists()
        assertAbsent("learn:dict-n:crew")

        click("learn:dict-know-n:sheet#paper")
        assertEquals("n:sheet#paper", runtime.storage.entries["lex.known"])
        click("learn:filter-known")
        node("learn:dict-n:sheet#paper").assertExists()
        click("learn:filter-unknown")
        node("learn:dict-empty").assertExists()

        click("learn:filter-all")
        node("learn:dict-search").performTextClearance()
        idle()
        node("learn:dict-n:life jacket").assertExists()
        click("learn:dict-n:life jacket")
        settled("learn:word-page")
        node("learn:word-not-filled").assertExists()
        click("learn:word-back")
        settled("learn:dictionary")

        click("learn:dict-back")
        settled("learn:list")
    }

    @Test
    fun forgettingWhatWasSeenTakesASecondPressAndKeepsWhatIsKnown() = runAppTest(app(), prepare = {
        it.storage.entries["lex.seen"] = "n:crew,n:sheet"
        it.storage.entries["lex.known"] = "n:crew"
    }) {
        settled("learn:dict-open")
        click("learn:dict-open")
        settled("learn:dictionary")
        click("learn:dict-forget")
        assertEquals("n:crew,n:sheet", runtime.storage.entries["lex.seen"])
        click("learn:dict-forget")
        assertNull(runtime.storage.entries["lex.seen"])
        assertEquals("n:crew", runtime.storage.entries["lex.known"])
        node("learn:dict-empty").assertExists()
        assertAbsent("learn:dict-forget")
    }
}

private object SilentSpeech : Speech {
    override val available: Boolean = true

    override suspend fun speak(text: String) = Unit

    override fun stop() = Unit
}

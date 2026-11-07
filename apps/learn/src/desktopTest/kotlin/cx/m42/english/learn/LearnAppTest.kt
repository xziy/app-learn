package cx.m42.english.learn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.testing.AppContractTest
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.runAppTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** The library's TCK: whatever this app grows into, it stays a well-behaved Superizer app. */
class LearnContractTest : AppContractTest(LearnApp())

private class RecordingSpeech(override val available: Boolean = true, private val speakMillis: Long = 0) : Speech {
    val said = mutableListOf<String>()
    var stops = 0

    override suspend fun speak(text: String) {
        said += text
        delay(speakMillis)
    }

    override fun stop() {
        stops++
    }
}

private fun sentence(tag: String) = Sentence("ours $tag", "thought $tag", "english $tag", "[тр**а**нс $tag]")

private fun verb(word: String, tenses: Int = 2) = Verb(
    word,
    "meaning of $word",
    List(tenses) { t ->
        Tense(Voice.Active, Aspect.Simple, Time.entries[t % 3], form = "form $t", sentences = List(3) { e -> sentence("$word t$t e$e") })
    },
)

/**
 * A base of [count] verbs in memory, as JSON in the shape the skill writes. Each has six tenses of one phrase,
 * so its pages go: 0 its card, then a tense's card and its phrase six times, 13 pages in all. With [closedPassive]
 * a 14th says there is no passive.
 */
private fun base(count: Int, described: Boolean = true, closedPassive: Boolean = false): VerbSource = object : VerbSource {
    private val words = List(count) { "verb$it" }

    override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: всё подряд"))

    override suspend fun index(theme: String) = words

    override suspend fun read(theme: String, word: String): String {
        fun example(t: Int) = """{"ours":"ours $word t$t","thought":"thought $word t$t","english":"english $word t$t","transcription":"[тр**а**нс]"}"""
        fun cell(t: Int) = """{"form":"form $t","examples":[${example(t)}]}"""
        fun form(w: String) = """{"word":"$w","transcription":"[$w-з**в**ук]"}"""
        val about = if (!described) "" else """"notes":"notes of $word","forms":{
            "base":[${form(word)}],"past":[${form("${word}ed")},${form("${word}old")}],"participle":[${form("${word}en")}],
            "ing":[${form("${word}ing")}],"s":[${form("${word}s")}]},"""
        val passive = if (!closedPassive) "" else ""","passive":{"not_possible":"no `such` passive"}"""
        return """{"verb":"$word","meaning":"meaning of $word",$about"active":{
            "simple":{"past":${cell(0)},"present":${cell(1)},"future":${cell(2)}},
            "continuous":{"past":${cell(3)},"present":${cell(4)},"future":${cell(5)}}}$passive}"""
    }
}

/**
 * Two themes that share `verb0` with other phrases in it, as real ones do: `general` of [base], and `yachting` with
 * `verb0` to `verb11` in the other direction, so its first ten are not those of the other.
 */
private fun twoThemes(): VerbSource = object : VerbSource {
    private val general = base(12)

    override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: дом, работа"), Theme("yachting", "Яхтинг: паруса и снасти"))

    override suspend fun index(theme: String) = general.index(theme).let { if (theme == "yachting") it.reversed() else it }

    override suspend fun read(theme: String, word: String) =
        general.read(theme, word).let { if (theme == "yachting") it.replace("ours ", "ours at sea ") else it }
}

/**
 * Verbs with more to read than any window holds, wherever a verb can be long: its notes, the form on a cover, each step
 * of a phrase. The pages of each: 0 its card, 1 a cover, 2 the long phrase, 3 a short one.
 */
private fun longBase(): VerbSource = object : VerbSource {
    private val long = "long text ".repeat(500).trim()

    override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: всё подряд"))

    override suspend fun index(theme: String) = listOf("verb0", "verb1")

    override suspend fun read(theme: String, word: String) =
        """{"verb":"$word","meaning":"meaning of $word","notes":"$long","active":{"simple":{"past":{"form":"$long","examples":[
            {"ours":"$long","thought":"$long","english":"$long","transcription":"[$long]"},
            {"ours":"short","thought":"short","english":"short","transcription":"[short]"}]}}}}"""
}

class ThemesTest {
    @Test
    fun aThemeHasItsNameBeforeTheColonOfItsDescriptionAndWhatItIsAboutAfter() {
        val theme = Theme("yachting", "Яхтинг: паруса и снасти. Среда: море.")
        assertEquals("Яхтинг", theme.russianTitle)
        assertEquals("Паруса и снасти. Среда: море.", theme.russianAbout)
        assertEquals("Yachting", theme.englishTitle)
    }

    @Test
    fun aDescriptionWithoutANameLeavesTheNameOfTheFolder() {
        val theme = Theme("office-work", "работа в офисе")
        assertEquals("Office work", theme.russianTitle)
        assertEquals("Работа в офисе", theme.russianAbout)
    }

    @Test
    fun themesKeepTheOrderOfTheFile() {
        assertEquals(
            listOf(Theme("zebra", "З: з"), Theme("apple", "Я: я")),
            parseThemes("""{"zebra":"З: з","apple":"Я: я"}"""),
        )
    }

    @Test
    fun inEnglishAThemeIsJustItsName() {
        val theme = Theme("yachting", "Яхтинг: паруса и снасти")
        assertEquals("Яхтинг", LearnStrings.Ru.themeTitle(theme))
        assertEquals("Паруса и снасти", LearnStrings.Ru.themeAbout(theme))
        assertEquals("Yachting", LearnStrings.En.themeTitle(theme))
        assertEquals(null, LearnStrings.En.themeAbout(theme))
    }
}

class VerbsTest {
    @Test
    fun aVerbsTensesGoFromActiveSimpleToFutureInThePastAndKnowWhereTheyAre() {
        fun cell(tag: String) = """{"form":"x $tag","examples":[{"ours":"$tag","thought":"t","english":"e","transcription":"[т]"}]}"""
        val verb = parseVerb(
            """{"verb":"wash","meaning":"мыть",
            "future_in_past":${cell("fip")},
            "passive":{"simple":{"past":${cell("pp")}},"perfect_continuous":{"not_possible":"нет типа"}},
            "active":{"simple":{"present":${cell("as2")},"past":${cell("as1")}},
                      "continuous":{"not_possible":"нет вида"},
                      "perfect":{"future":{"form":"will","not_possible":"нет клетки"},"past":${cell("ap1")}}}}""",
        )
        assertEquals("wash", verb.word)
        assertEquals("мыть", verb.meaning)
        assertEquals(
            listOf("as1", "as2", null, "ap1", null, "pp", null, "fip"),
            verb.tenses.map { it.sentences.singleOrNull()?.ours },
        )
        assertEquals(Tense(Voice.Active, Aspect.Simple, Time.Past, "x as1", verb.tenses[0].sentences), verb.tenses[0])
        assertEquals(Tense(Voice.Active, Aspect.Continuous, notPossible = "нет вида"), verb.tenses[2])
        assertEquals(Tense(Voice.Active, Aspect.Perfect, Time.Future, "will", notPossible = "нет клетки"), verb.tenses[4])
        assertEquals(null, verb.tenses.last().voice, "the future in the past stands apart")

        // Nothing to read about it, and still a verb with its cards.
        assertEquals("", verb.notes)
        assertEquals(emptyMap(), verb.forms)
        assertEquals(null, verb.transcription)
    }

    @Test
    fun aWholeVoiceCanBeClosed() {
        val verb = parseVerb("""{"verb":"abate","meaning":"утихать","passive":{"not_possible":"без объекта"}}""")
        assertEquals(listOf(Tense(Voice.Passive, notPossible = "без объекта")), verb.tenses)
    }

    @Test
    fun aVerbsFormsKeepTheirVariantsAndItsTranscriptionIsThatOfTheBaseForm() {
        val verb = parseVerb(
            """{"verb":"abide","meaning":"терпеть","notes":"две формы прошедшего",
            "forms":{"s":[{"word":"abides","transcription":"[эб**а**йдз]"}],
                     "past":[{"word":"abode","transcription":"[эб**о**уд]"},{"word":"abided","transcription":"[эб**а**йдид]"}],
                     "base":[{"word":"abide","transcription":"[эб**а**йд]"}]}}""",
        )
        assertEquals("две формы прошедшего", verb.notes)
        assertEquals("[эб**а**йд]", verb.transcription)
        assertEquals(listOf("abode", "abided"), verb.forms.getValue(FormKind.Past).map { it.word })
        assertEquals(listOf(FormKind.Base, FormKind.Past, FormKind.S), verb.forms.keys.toList(), "in the order they are read, and only those given")
    }

    @Test
    fun theBaseTheAppCarriesReadsWholeInEveryThemeAndEveryWordHasItsCards() = runBlocking {
        val themes = ResourceVerbSource.themes()
        assertTrue(themes.any { it.name == DefaultTheme }, "the theme that opens at first is there: $themes")
        assertTrue(themes.size > 1, "there is a theme to choose: $themes")
        for (theme in themes) {
            assertTrue(':' in theme.description, "${theme.name} has a name in its description")
            val words = ResourceVerbSource.index(theme.name)
            assertTrue(words.size >= WordsAtOnce, "${theme.name}: enough verbs for a round, got ${words.size}")
            val verbs = words.map { parseVerb(ResourceVerbSource.read(theme.name, it)) }
            assertEquals(words, verbs.map { it.word }, "${theme.name}: each file is the verb it is named after")
            for (verb in verbs) {
                val where = "${theme.name}/${verb.word}"
                assertEquals(FormKind.entries, verb.forms.keys.toList(), "$where has all its forms")
                assertTrue(verb.forms.values.flatten().all { it.word.isNotBlank() && it.transcription.isNotBlank() }, where)
                assertTrue(verb.notes.isNotBlank(), "$where has notes")
                assertTrue(verb.tenses.flatMap { it.sentences }.isNotEmpty(), "$where has phrases")
                for (tense in verb.tenses) {
                    assertTrue((tense.sentences.isEmpty()) == (tense.notPossible != null), "$where: a tense has phrases or says why not: $tense")
                }
                for (s in verb.tenses.flatMap { it.sentences }) {
                    assertTrue(listOf(s.ours, s.thought, s.english, s.transcription).all { it.isNotBlank() }, "$where: $s")
                }
            }
        }
    }

    @Test
    fun theListOfAThemeGoesInTheOrderOfItsJournalNotOfTheAlphabet() = runBlocking {
        assertEquals(listOf("be", "have", "do"), ResourceVerbSource.index("yachting").take(3))
    }
}

class FeedTest {
    @Test
    fun aVerbOpensWithItsCardAndEachTenseWithItsOwn() {
        val a = verb("a")
        val b = verb("b")
        val pages = buildPages(listOf(a, b))
        assertEquals(2 * (1 + 2 * (1 + 3)), pages.size)
        assertEquals(FeedPage.VerbCard(a), pages[0])
        assertEquals(FeedPage.TenseCard(a, a.tenses[0], Portion(1, 2, 3)), pages[1])
        assertEquals(FeedPage.Phrase(a, a.tenses[0], a.tenses[0].sentences[0]), pages[2])
        assertEquals(FeedPage.Phrase(a, a.tenses[0], a.tenses[0].sentences[2]), pages[4])
        assertEquals(FeedPage.TenseCard(a, a.tenses[1], Portion(2, 2, 3)), pages[5])
        assertEquals(FeedPage.VerbCard(b), pages[9])
    }

    @Test
    fun aTenseThatDoesNotExistIsACardWithNoPhrasesAndNoPortion() {
        val closed = Tense(Voice.Passive, notPossible = "нет")
        val closing = Verb("x", "m", listOf(closed))
        assertEquals(listOf(FeedPage.VerbCard(closing), FeedPage.TenseCard(closing, closed, null)), buildPages(listOf(closing)))
    }

    @Test
    fun aTenseThatDoesNotExistIsNotCountedAmongThePortions() {
        val open = Tense(Voice.Active, Aspect.Simple, Time.Past, sentences = listOf(sentence("a"), sentence("b")))
        val closed = Tense(Voice.Active, Aspect.Continuous, notPossible = "нет")
        val last = Tense(Voice.Active, Aspect.Perfect, Time.Past, sentences = listOf(sentence("c")))
        val portions = buildPages(listOf(Verb("x", "m", listOf(open, closed, last))))
            .filterIsInstance<FeedPage.TenseCard>().map { it.portion }
        assertEquals(listOf(Portion(1, 2, 2), null, Portion(2, 2, 1)), portions)
    }

    @Test
    fun aPortionIsCountedInBothLanguages() {
        fun ru(cards: Int) = LearnStrings.Ru.portion(Portion(3, 14, cards))
        fun en(cards: Int) = LearnStrings.En.portion(Portion(3, 14, cards))
        assertEquals("Порция 3 из 14 · 1 карточка", ru(1))
        assertEquals("Порция 3 из 14 · 3 карточки", ru(3))
        assertEquals("Порция 3 из 14 · 5 карточек", ru(5))
        assertEquals("Порция 3 из 14 · 11 карточек", ru(11))
        assertEquals("Порция 3 из 14 · 21 карточка", ru(21))
        assertEquals("Portion 3 of 14 · 1 card", en(1))
        assertEquals("Portion 3 of 14 · 3 cards", en(3))
    }

    @Test
    fun aVerbsColourIsItsOwnDarkAndNeverBlack() {
        val words = listOf("abandon", "abate", "abhor", "abide", "abolish", "abort", "abrogate", "absorb", "abstain", "accede")
        val colours = words.map(::verbColor)
        assertEquals(colours, words.map(::verbColor), "the same verb, the same colour")
        assertTrue(colours.distinct().size >= 8, "verbs are told apart: $colours")
        for (colour in colours) {
            assertTrue(maxOf(colour.red, colour.green, colour.blue) < 0.4f, "dark enough for white text: $colour")
            assertTrue(maxOf(colour.red, colour.green, colour.blue) > 0.1f, "not black: $colour")
            assertNotEquals(Color.Black, colour)
        }
    }

    @Test
    fun theStressedVowelIsSetOffAndTheMarkersGo() {
        val text = stressed("[й**э**стэдэй экс**э**пт]", Color.Red)
        assertEquals("[йэстэдэй эксэпт]", text.text)
        assertEquals(listOf("э", "э"), text.spanStyles.map { text.text.substring(it.start, it.end) })
    }
}

@OptIn(ExperimentalTestApi::class)
class LearnScreenTest {
    private fun AppTestScope<Unit>.feed(): SemanticsNodeInteraction = node("learn:feed")

    private fun AppTestScope<Unit>.settled(tag: String) {
        ui.waitUntil(timeoutMillis = 5_000L) { runCatching { node(tag).assertExists() }.isSuccess }
        idle()
    }

    /** From the list into the feed, where the person stopped or at the start. */
    private fun AppTestScope<Unit>.openFeed() {
        settled("learn:go")
        click("learn:go")
    }

    private fun AppTestScope<Unit>.next() {
        node("learn:feed").performTouchInput { swipeLeft() }
        idle()
    }

    /** The list may have to be scrolled to reach it. */
    private fun AppTestScope<Unit>.reach(tag: String) {
        node("learn:list").performScrollToNode(hasTestTag(tag))
        idle()
    }

    /** Nothing random here: a round is the first verbs of what it is drawn from, in their order. */
    private fun app(speech: Speech = RecordingSpeech(), count: Int = 12) = LearnApp(base(count), speech, shuffle = { it })

    /** A stored place, in a stored choice of ten: a verb, a page among its pages, a step. Page 2 is the first phrase of a verb. */
    private fun at(place: String): (cx.m42.superizer.testing.FakeAppRuntime) -> Unit = {
        it.storage.entries["place"] = place
        it.storage.entries["chosen"] = List(10) { n -> "verb$n" }.joinToString(",")
    }

    @Test
    fun itAsksForTheWholeScreen() = runAppTest(app()) {
        assertTrue((instance.chrome.value as AppChrome).fullscreen)
    }

    // The list

    @Test
    fun itOpensOnTheListWithARoundOfFiveAndTheRestToAddByHand() = runAppTest(app()) {
        settled("learn:list")
        assertAbsent("learn:feed")
        node("learn:verb-verb0").assertExists()
        reach("learn:verb-verb4")
        assertAbsent("learn:verb-verb5")
        reach("learn:add-verb5")
        reach("learn:add-verb11")
        assertAbsent("learn:add-verb0")
    }

    @Test
    fun theChoiceIsChangedInTheListAndKept() = runAppTest(app()) {
        settled("learn:list")
        reach("learn:add-verb11")
        click("learn:add-verb11")
        reach("learn:verb-verb11")
        assertEquals(List(5) { "verb$it" }.plus("verb11").joinToString(","), runtime.storage.entries["chosen"])

        reach("learn:remove-verb0")
        click("learn:remove-verb0")
        assertAbsent("learn:verb-verb0")
        reach("learn:add-verb0")
        assertEquals(List(4) { "verb${it + 1}" }.plus("verb11").joinToString(","), runtime.storage.entries["chosen"])
    }

    @Test
    fun theFeedGoesThroughTheChosenVerbsOnly() = runAppTest(app(), prepare = { it.storage.entries["chosen"] = "verb5,verb3" }) {
        openFeed()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb5")
        assertAbsent("learn:go")

        // Its 13 pages later comes the next chosen verb, not the next of the base.
        click("learn:to-list")
        click("learn:verb-verb3")
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb3")
    }

    @Test
    fun aRowOpensTheFeedAtThatVerb() = runAppTest(app()) {
        settled("learn:list")
        click("learn:verb-verb3")
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb3")
    }

    @Test
    fun withNothingChosenTheListSaysSoAndThereIsNothingToStart() = runAppTest(app(), prepare = { it.storage.entries["chosen"] = "" }) {
        settled("learn:none-chosen")
        assertAbsent("learn:go")
        reach("learn:add-verb0")
    }

    @Test
    fun theArrowsGoFromTheFeedToTheListAndFromTheListOut() = runAppTest(app()) {
        openFeed()
        settled("learn:feed")
        assertAbsent("learn:close")
        click("learn:to-list")
        settled("learn:list")
        assertEquals(0, runtime.navigation.closed)
        click("learn:close")
        assertEquals(1, runtime.navigation.closed)
    }

    // Themes

    private fun themed() = LearnApp(twoThemes(), RecordingSpeech(), shuffle = { it })

    @Test
    fun withOneThemeThereIsNothingToChooseFrom() = runAppTest(app()) {
        settled("learn:list")
        assertAbsent("learn:theme-general")
        assertAbsent("learn:theme-about")
    }

    @Test
    fun theDefaultThemeOpensFirstAndTheListSaysWhatItIsAbout() = runAppTest(themed()) {
        settled("learn:theme-yachting")
        node("learn:theme-general").assertExists()
        node("learn:theme-about").assertTextEquals("Дом, работа")
        click("learn:verb-verb0")
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb0")
    }

    @Test
    fun anotherThemeHasItsOwnVerbsAndItsOwnPhrasesAndIsKept() = runAppTest(themed(), prepare = at("verb0:2:0")) {
        settled("learn:theme-yachting")
        click("learn:theme-yachting")
        settled("learn:verb-verb11")
        node("learn:theme-about").assertTextEquals("Паруса и снасти")
        assertEquals("yachting", runtime.storage.entries["theme"])
        // Its round is its own: verb11 to verb7 here, so verb0 is left to add.
        assertAbsent("learn:verb-verb0")
        reach("learn:add-verb0")

        // The stored place was in the other theme, so this one starts from its top.
        reach("learn:go")
        click("learn:go")
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb11")
        tap()
        settled("learn:tense-card")
        tap()
        settled("learn:ours")
        node("learn:ours").assertTextEquals("ours at sea verb11 t0")
    }

    @Test
    fun aStoredThemeOpensByItself() = runAppTest(themed(), prepare = { it.storage.entries["theme"] = "yachting" }) {
        settled("learn:verb-verb11")
        node("learn:theme-about").assertTextEquals("Паруса и снасти")
    }

    @Test
    fun aThemeThatIsGoneOpensTheDefaultOne() = runAppTest(themed(), prepare = { it.storage.entries["theme"] = "gone" }) {
        settled("learn:verb-verb0")
        node("learn:theme-about").assertTextEquals("Дом, работа")
    }

    @Test
    fun eachThemeKeepsItsOwnChoiceAndItsOwnPlaceAndTheOldKeysStayTheDefaultThemes() = runAppTest(
        themed(),
        prepare = {
            it.storage.entries["chosen"] = "verb1,verb2"
            it.storage.entries["place"] = "verb1:2:1"
        },
    ) {
        settled("learn:theme-yachting")
        click("learn:theme-yachting")
        settled("learn:verb-verb11")
        click("learn:remove-verb11")
        assertAbsent("learn:verb-verb11")
        assertEquals(List(4) { "verb${10 - it}" }.joinToString(","), runtime.storage.entries["chosen.yachting"])
        assertEquals("verb1,verb2", runtime.storage.entries["chosen"], "the other theme's choice is as it was")

        click("learn:verb-verb8")
        settled("learn:verb-card")
        tap()
        settled("learn:tense-card")
        assertEquals("verb8:1:0", runtime.storage.entries["place.yachting"])
        assertEquals("verb1:2:1", runtime.storage.entries["place"], "and so is its place")

        // Back in the first theme: its choice, and it goes on from where it was left.
        click("learn:to-list")
        settled("learn:theme-general")
        click("learn:theme-general")
        settled("learn:verb-verb1")
        assertAbsent("learn:verb-verb8")
        click("learn:go")
        settled("learn:thought")
        node("learn:word").assertTextEquals("verb1")
    }

    @Test
    fun theThemeChosenLastWinsOverOneThatIsStillLoading() {
        val gate = CompletableDeferred<Unit>()
        val themes = twoThemes()
        val slowAtSea = object : VerbSource by themes {
            override suspend fun read(theme: String, word: String): String {
                if (theme == "yachting") gate.await()
                return themes.read(theme, word)
            }
        }
        runAppTest(LearnApp(slowAtSea, RecordingSpeech(), shuffle = { it })) {
            settled("learn:theme-yachting")
            click("learn:theme-yachting")
            // Back to the one still on screen, while the other is on its way.
            click("learn:theme-general")
            gate.complete(Unit)
            idle()
            settled("learn:verb-verb0")
            node("learn:theme-about").assertTextEquals("Дом, работа")
            assertEquals(DefaultTheme, runtime.storage.entries["theme"])
        }
    }

    // A round: five at random of what has not been seen

    private fun AppTestScope<Unit>.stored(key: String): String? = runtime.storage.entries[key]

    private fun AppTestScope<Unit>.seenCountIs(count: Int) {
        settled("learn:list")
        reach("learn:seen-open")
        node("learn:seen-open").assertTextEquals(LearnStrings.Ru.seenTitle, "$count")
    }

    @Test
    fun aRoundIsFiveDifferentVerbsAtRandomOfTheBase() {
        val rounds = List(5) {
            var round = emptyList<String>()
            runAppTest(LearnApp(base(60), RecordingSpeech())) {
                settled("learn:list")
                round = stored("chosen").orEmpty().split(',')
                round.forEach { word -> reach("learn:verb-$word") }
            }
            round
        }
        for (round in rounds) {
            assertEquals(WordsAtOnce, round.toSet().size, "$round")
            assertTrue(round.all { it.removePrefix("verb").toInt() in 0 until 60 }, "$round")
        }
        assertTrue(rounds.toSet().size > 1, "not the same ones each time: $rounds")
    }

    @Test
    fun aRoundIsNotDrawnFromTheSeenOnes() = runAppTest(
        LearnApp(base(60), RecordingSpeech()),
        prepare = { it.storage.entries["seen"] = List(55) { n -> "verb$n" }.joinToString(",") },
    ) {
        settled("learn:list")
        assertEquals(List(5) { "verb${55 + it}" }, stored("chosen").orEmpty().split(',').sortedBy { it.removePrefix("verb").toInt() })
    }

    @Test
    fun aRoundIsKeptUntilItIsGoneThrough() = runAppTest(
        LearnApp(base(60), RecordingSpeech()),
        prepare = { it.storage.entries["chosen"] = "verb7,verb3" },
    ) {
        settled("learn:verb-verb7")
        node("learn:verb-verb3").assertExists()
        assertEquals("verb7,verb3", stored("chosen"))
    }

    @Test
    fun theButtonDrawsAnotherRound() = runAppTest(app()) {
        settled("learn:pick")
        click("learn:pick")
        settled("learn:verb-verb5")
        assertEquals(List(5) { "verb${5 + it}" }.joinToString(","), stored("chosen"))
        assertAbsent("learn:verb-verb0")
        reach("learn:add-verb0")
    }

    @Test
    fun theButtonGoesForOtherVerbsAndFillsUpWithTheseIfThereAreTooFew() = runAppTest(app(count = 7)) {
        settled("learn:pick")
        click("learn:pick")
        settled("learn:verb-verb5")
        assertEquals("verb5,verb6,verb0,verb1,verb2", stored("chosen"))
    }

    @Test
    fun withNothingElseToDrawThereIsNoButton() = runAppTest(app(count = 3)) {
        settled("learn:verb-verb2")
        assertAbsent("learn:pick")
    }

    @Test
    fun withNothingChosenTheButtonDrawsARoundAndRemovingAllIsNotARoundByItself() = runAppTest(
        app(),
        prepare = { it.storage.entries["chosen"] = "verb3" },
    ) {
        settled("learn:remove-verb3")
        click("learn:remove-verb3")
        settled("learn:none-chosen")
        assertEquals("", stored("chosen"))
        click("learn:pick")
        settled("learn:verb-verb0")
    }

    // What counts as seen

    @Test
    fun goingForwardOutOfAVerbMarksItSeen() = runAppTest(app(), prepare = at("verb0:12:2")) {
        openFeed()
        settled("learn:english")
        assertEquals(null, stored("seen"), "at its last card it is not done yet")
        tap()
        settled("learn:verb-card")
        assertEquals("verb0", stored("seen"))
    }

    @Test
    fun aVerbLeftHalfwayIsNotSeenAndStaysChosen() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        tap()
        settled("learn:thought")
        feed().performKeyInput { pressKey(Key.DirectionDown) }
        settled("learn:tense-card")
        click("learn:to-list")
        settled("learn:verb-verb0")
        assertEquals(null, stored("seen"))
        seenCountIs(0)
    }

    @Test
    fun goingBackIntoAVerbDoesNotMarkIt() = runAppTest(app()) {
        settled("learn:verb-verb1")
        click("learn:verb-verb1")
        settled("learn:verb-card")
        feed().performKeyInput { pressKey(Key.DirectionUp) }
        settled("learn:ours")
        node("learn:word").assertTextEquals("verb0")
        assertEquals(null, stored("seen"))
    }

    @Test
    fun aSeenVerbStaysInTheFeedAndLeavesTheChosenOnTheWayBack() = runAppTest(app(), prepare = at("verb0:12:2")) {
        openFeed()
        settled("learn:english")
        tap()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")

        // The feed does not jump under the person: its pages are the same while it is open.
        feed().performKeyInput { pressKey(Key.DirectionUp) }
        settled("learn:english")
        node("learn:word").assertTextEquals("verb0")
        feed().performKeyInput { pressKey(Key.DirectionDown) }
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")
        assertEquals("verb0", stored("seen"), "marked once")

        click("learn:to-list")
        settled("learn:verb-verb1")
        assertAbsent("learn:verb-verb0")
        seenCountIs(1)
        assertAbsent("learn:add-verb0")
    }

    @Test
    fun whenTheWholeRoundIsSeenTheNextIsDrawnOfTheRest() = runAppTest(
        app(),
        prepare = {
            it.storage.entries["chosen"] = "verb0"
            it.storage.entries["place"] = "verb0:12:2"
        },
    ) {
        openFeed()
        settled("learn:english")
        tap()
        settled("learn:done")
        assertEquals("verb0", stored("seen"))
        assertEquals(null, stored("place"))

        click("learn:finish-list")
        settled("learn:verb-verb1")
        assertEquals(List(5) { "verb${it + 1}" }.joinToString(","), stored("chosen"))
        assertAbsent("learn:verb-verb0")
        node("learn:go").assertExists()
    }

    @Test
    fun whenAllAreSeenTheListSaysSoAndTheFirstMarkTakenOffStartsARound() = runAppTest(
        app(count = 3),
        prepare = {
            it.storage.entries["chosen"] = "verb0"
            it.storage.entries["seen"] = "verb0,verb1,verb2"
        },
    ) {
        settled("learn:all-seen")
        assertAbsent("learn:go")
        assertAbsent("learn:pick")
        assertEquals(null, stored("chosen"))

        click("learn:seen-open")
        settled("learn:seen-verb-verb1")
        node("learn:seen-verb-verb1").performTouchInput { swipeLeft() }
        idle()
        click("learn:seen-back")
        settled("learn:verb-verb1")
        assertEquals("verb1", stored("chosen"))
    }

    // The list of the seen ones

    private fun threeSeen(): (cx.m42.superizer.testing.FakeAppRuntime) -> Unit = {
        it.storage.entries["chosen"] = "verb5,verb6"
        it.storage.entries["seen"] = "verb0,verb1,verb2"
        it.storage.entries["place"] = "verb5:2:1"
    }

    private fun AppTestScope<Unit>.openSeen() {
        settled("learn:seen-open")
        click("learn:seen-open")
        settled("learn:seen")
    }

    @Test
    fun theSeenOnesHaveTheirOwnPageLatestOnTopAndTheArrowGoesBackToTheList() = runAppTest(app(), prepare = threeSeen()) {
        seenCountIs(3)
        openSeen()
        assertAbsent("learn:list")
        settled("learn:seen-hint")
        val tops = listOf("verb2", "verb1", "verb0").map { node("learn:seen-verb-$it").getUnclippedBoundsInRoot().top }
        assertEquals(tops.sortedBy { it.value }, tops)

        click("learn:seen-back")
        settled("learn:list")
        assertEquals(0, runtime.navigation.closed)
    }

    @Test
    fun aSeenRowSaysHowItIsSaidAndWhatItMeans() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        ui.onNodeWithText("meaning of verb1").assertExists()
        ui.onNodeWithText("[verb1-звук]").assertExists()
    }

    @Test
    fun withNothingSeenThePageSaysSoAndHasNoButton() = runAppTest(app()) {
        openSeen()
        settled("learn:seen-none")
        assertAbsent("learn:unsee-all")
    }

    @Test
    fun aSeenVerbCanBeSeenAgainAloneAndThatChangesNothing() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        click("learn:seen-verb-verb1")
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")
        tap()
        settled("learn:tense-card")
        tap()
        settled("learn:ours")
        node("learn:ours").assertTextEquals("ours verb1 t0")

        // To the end of it: there is one verb in this feed, and then the end.
        repeat(11) { feed().performKeyInput { pressKey(Key.DirectionDown) } }
        settled("learn:done")
        node("learn:done").assertTextEquals(LearnStrings.Ru.againDone)
        click("learn:finish-list")

        settled("learn:seen")
        assertEquals("verb0,verb1,verb2", stored("seen"))
        assertEquals("verb5,verb6", stored("chosen"))
        assertEquals("verb5:2:1", stored("place"), "the round goes on from where it was")

        click("learn:seen-back")
        settled("learn:go")
        click("learn:go")
        settled("learn:thought")
        node("learn:word").assertTextEquals("verb5")
    }

    @Test
    fun theArrowOutOfASecondLookGoesBackToTheSeenOnes() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        click("learn:seen-verb-verb1")
        settled("learn:verb-card")
        click("learn:to-list")
        settled("learn:seen")
    }

    @Test
    fun aSwipeEitherWayTakesTheMarkOffAndTheVerbIsThereToChooseAgain() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        node("learn:seen-verb-verb1").performTouchInput { swipeLeft() }
        idle()
        assertAbsent("learn:seen-verb-verb1")
        assertAbsent("learn:feed")
        assertEquals("verb0,verb2", stored("seen"))

        node("learn:seen-verb-verb0").performTouchInput { swipeRight() }
        idle()
        assertAbsent("learn:seen-verb-verb0")
        assertEquals("verb2", stored("seen"))

        click("learn:seen-back")
        settled("learn:list")
        seenCountIs(1)
        reach("learn:add-verb0")
        reach("learn:add-verb1")
        assertAbsent("learn:add-verb2")
    }

    @Test
    fun aVerbSeenFromThisRoundComesBackToItWithItsMarkOff() = runAppTest(
        app(),
        prepare = {
            it.storage.entries["chosen"] = "verb0,verb1"
            it.storage.entries["seen"] = "verb0"
        },
    ) {
        settled("learn:verb-verb1")
        assertAbsent("learn:verb-verb0")
        openSeen()
        settled("learn:seen-verb-verb0")
        node("learn:seen-verb-verb0").performTouchInput { swipeLeft() }
        idle()
        click("learn:seen-back")
        settled("learn:verb-verb0")
    }

    @Test
    fun aMouseSwipesARowOffToo() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        node("learn:seen-verb-verb1").performMouseInput {
            moveTo(center)
            press()
            repeat(6) {
                moveBy(Offset(-width / 8f, 0f))
                advanceEventTime(100)
            }
            release()
        }
        idle()
        assertAbsent("learn:seen-verb-verb1")
        assertAbsent("learn:feed")
        assertEquals("verb0,verb2", stored("seen"))
    }

    @Test
    fun aSlowShortSwipePutsTheRowBackAndAQuickShortOneTakesItOff() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        val left = node("learn:seen-verb-verb1").getUnclippedBoundsInRoot().left
        node("learn:seen-verb-verb1").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(-width * 0.05f, 0f), delayMillis = 200) }
            advanceEventTime(300)
            up()
        }
        idle()
        assertEquals("verb0,verb1,verb2", stored("seen"))
        assertEquals(left, node("learn:seen-verb-verb1").getUnclippedBoundsInRoot().left)

        node("learn:seen-verb-verb1").performTouchInput {
            down(center)
            repeat(3) { moveBy(Offset(-width * 0.08f, 0f), delayMillis = 8) }
            up()
        }
        idle()
        assertAbsent("learn:seen-verb-verb1")
        assertEquals("verb0,verb2", stored("seen"))
    }

    @Test
    fun twoSwipesInARowBothCount() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:seen-verb-verb1")
        node("learn:seen-verb-verb1").performTouchInput { swipeLeft() }
        node("learn:seen-verb-verb2").performTouchInput { swipeLeft() }
        idle()
        assertEquals("verb0", stored("seen"))
    }

    @Test
    fun theButtonForAllTakesASecondPressAndForgetsTheFirst() = runAppTest(app(), prepare = threeSeen()) {
        openSeen()
        settled("learn:unsee-all")
        click("learn:unsee-all")
        assertEquals("verb0,verb1,verb2", stored("seen"), "one press is not enough")
        ui.onNodeWithText(LearnStrings.Ru.unseeAllSure).assertExists()

        ui.mainClock.autoAdvance = false
        advance(5_000)
        ui.mainClock.autoAdvance = true
        idle()
        ui.onNodeWithText(LearnStrings.Ru.unseeAll).assertExists()

        click("learn:unsee-all")
        click("learn:unsee-all")
        settled("learn:seen-none")
        assertAbsent("learn:seen-verb-verb0")
        assertAbsent("learn:unsee-all")
        assertEquals("", stored("seen"))
        assertEquals("verb5,verb6", stored("chosen"), "what is chosen stays")

        click("learn:seen-back")
        settled("learn:list")
        seenCountIs(0)
        reach("learn:add-verb0")
    }

    @Test
    fun eachThemeHasItsOwnSeenVerbs() = runAppTest(themed(), prepare = { it.storage.entries["seen"] = "verb0,verb1" }) {
        settled("learn:theme-yachting")
        seenCountIs(2)

        reach("learn:theme-yachting")
        click("learn:theme-yachting")
        settled("learn:verb-verb11")
        seenCountIs(0)
        assertEquals("verb0,verb1", stored("seen"))
        assertEquals(null, stored("seen.yachting"))
    }

    @Test
    fun aSeenThemeIsMarkedInItsOwnKey() = runAppTest(
        LearnApp(twoThemes(), RecordingSpeech(), shuffle = { it }),
        prepare = {
            it.storage.entries["theme"] = "yachting"
            it.storage.entries["chosen.yachting"] = "verb3,verb4"
            it.storage.entries["place.yachting"] = "verb3:12:2"
        },
    ) {
        openFeed()
        settled("learn:english")
        tap()
        settled("learn:verb-card")
        assertEquals("verb3", stored("seen.yachting"))
        assertEquals(null, stored("seen"))
    }

    // Scrolling the lists: the wheel and the finger are the list's own, the mouse's drag is added

    private fun AppTestScope<Unit>.scrolledAway(tag: String) {
        idle()
        assertAbsent(tag)
    }

    @Test
    fun theListScrollsByFinger() = runAppTest(app(count = 60)) {
        settled("learn:go")
        node("learn:list").performTouchInput { swipeUp() }
        scrolledAway("learn:go")
        node("learn:list").performTouchInput { swipeDown() }
        idle()
        node("learn:go").assertExists()
    }

    @Test
    fun theListScrollsByTheWheel() = runAppTest(app(count = 60)) {
        settled("learn:go")
        node("learn:list").performMouseInput {
            moveTo(center)
            scroll(12f)
        }
        scrolledAway("learn:go")
        node("learn:list").performMouseInput { scroll(-12f) }
        idle()
        node("learn:go").assertExists()
    }

    private fun AppTestScope<Unit>.dragWithMouse(tag: String, up: Boolean, from: (Float) -> Float) = node(tag).performMouseInput {
        moveTo(Offset(center.x, from(height.toFloat())))
        press()
        repeat(8) { moveBy(Offset(0f, if (up) -70f else 70f)) }
        release()
    }

    @Test
    fun theListScrollsByAMouseDragAndThePressIsNoClick() = runAppTest(app(count = 60)) {
        settled("learn:go")
        dragWithMouse("learn:list", up = true) { it - 10f }
        scrolledAway("learn:go")
        assertAbsent("learn:feed")
        dragWithMouse("learn:list", up = false) { 10f }
        idle()
        node("learn:go").assertExists()

        // Begun on the button itself.
        node("learn:go").performMouseInput {
            moveTo(center)
            press()
            repeat(4) { moveBy(Offset(0f, -40f)) }
            release()
        }
        idle()
        assertAbsent("learn:feed")

        // A click is still a click.
        node("learn:list").performTouchInput { swipeDown() }
        idle()
        node("learn:go").performMouseInput { click() }
        idle()
        settled("learn:feed")
    }

    @Test
    fun aRowOfTheListStillOpensAndAddsAfterScrolling() = runAppTest(app(count = 60)) {
        settled("learn:go")
        node("learn:list").performTouchInput { swipeUp() }
        idle()
        reach("learn:add-verb40")
        click("learn:add-verb40")
        idle()
        assertEquals(List(5) { "verb$it" }.plus("verb40").joinToString(","), stored("chosen"))
    }

    private fun manySeen(): (cx.m42.superizer.testing.FakeAppRuntime) -> Unit = {
        it.storage.entries["chosen"] = "verb59"
        it.storage.entries["seen"] = List(50) { n -> "verb$n" }.joinToString(",")
    }

    @Test
    fun theSeenListScrollsByFingerWheelAndMouseAndUpAndDownTakeNoMarkOff() = runAppTest(app(count = 60), prepare = manySeen()) {
        openSeen()
        val all = stored("seen")
        settled("learn:seen-verb-verb49")

        node("learn:seen-list").performTouchInput { swipeUp() }
        scrolledAway("learn:seen-verb-verb49")
        node("learn:seen-list").performTouchInput { swipeDown() }
        idle()
        node("learn:seen-verb-verb49").assertExists()

        node("learn:seen-list").performMouseInput {
            moveTo(center)
            scroll(12f)
        }
        scrolledAway("learn:seen-verb-verb49")
        node("learn:seen-list").performMouseInput { scroll(-12f) }
        idle()
        node("learn:seen-verb-verb49").assertExists()

        dragWithMouse("learn:seen-list", up = true) { it - 10f }
        scrolledAway("learn:seen-verb-verb49")
        dragWithMouse("learn:seen-list", up = false) { 10f }
        idle()
        node("learn:seen-verb-verb49").assertExists()

        assertEquals(all, stored("seen"))
        assertAbsent("learn:feed")
    }

    @Test
    fun theButtonForAllStaysOnScreenWhileTheSeenListScrolls() = runAppTest(app(count = 60), prepare = manySeen()) {
        openSeen()
        settled("learn:unsee-all")
        val before = node("learn:unsee-all").getUnclippedBoundsInRoot().top
        node("learn:seen-list").performTouchInput { swipeUp() }
        idle()
        assertEquals(before, node("learn:unsee-all").getUnclippedBoundsInRoot().top)
    }

    @Test
    fun theRowsDoNotRunUnderTheArrow() = runAppTest(app(count = 60)) {
        settled("learn:go")
        val arrow = node("learn:close").getUnclippedBoundsInRoot().bottom
        assertTrue(node("learn:list").getUnclippedBoundsInRoot().top >= arrow)
    }

    // Scrolling the cards: what does not fit can be read, by every means there is, before the feed moves on

    private fun AppTestScope<Unit>.top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    private val longCards = listOf(
        "verb0:0:0" to "learn:about-word",
        "verb0:1:0" to "learn:tense-word",
        "verb0:2:0" to "learn:ours",
        "verb0:2:1" to "learn:thought",
        "verb0:2:2" to "learn:english",
    )

    private fun long() = LearnApp(longBase(), RecordingSpeech(), shuffle = { it })

    private fun onEachLongCard(check: AppTestScope<Unit>.(String) -> Unit) {
        for ((place, tag) in longCards) {
            runAppTest(long(), prepare = at(place)) {
                openFeed()
                settled(tag)
                check(tag)
            }
        }
    }

    @Test
    fun aCardThatDoesNotFitScrollsByAFingerAndOnlyANewSwipeFromItsEndGoesOn() = onEachLongCard { tag ->
        val place = stored("place")
        val before = top(tag)
        // Across the middle, where a thumb goes: the text of a phrase stops above the word at the bottom.
        fun swipe() = feed().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.2f) }
        swipe()
        idle()
        assertEquals(place, stored("place"), "$tag: still this card")
        assertTrue(top(tag) < before, "$tag: it moved")

        // However many it takes to read it: the swipe that reaches the end stays there, and the next one goes on.
        var swipes = 0
        while (stored("place") == place) {
            assertTrue(++swipes < 200, "$tag: it never ends")
            swipe()
            idle()
        }
        assertTrue(swipes >= 1, tag)
    }

    /** A flick: well under half of the screen, and fast. */
    private fun AppTestScope<Unit>.flick(up: Boolean) = feed().performTouchInput {
        down(center)
        repeat(3) { moveBy(Offset(0f, if (up) -40f else 40f), delayMillis = 8) }
        up()
    }

    @Test
    fun aFlickOnAPhraseThatFitsGoesToTheNextCardAndBack() = runAppTest(long(), prepare = at("verb0:3:0")) {
        openFeed()
        settled("learn:ours")
        flick(up = true)
        idle()
        assertEquals("verb1:0:0", stored("place"))
        flick(up = false)
        idle()
        assertEquals("verb0:3:0", stored("place"))
    }

    @Test
    fun aFlickOnACoverThatFitsGoesToTheNextCard() = runAppTest(app(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-card")
        flick(up = true)
        idle()
        assertEquals("verb0:2:0", stored("place"))
    }

    @Test
    fun aFlickOnACardThatDoesNotFitStaysOnItEvenAtItsEnd() = onEachLongCard { tag ->
        val place = stored("place")
        flick(up = true)
        idle()
        assertEquals(place, stored("place"), tag)
    }

    @Test
    fun aSlowSwipeLessThanHalfwayPutsTheCardBack() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        feed().performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, -20f), delayMillis = 100) }
            advanceEventTime(300)
            up()
        }
        idle()
        assertEquals("verb0:2:0", stored("place"))
        settled("learn:ours")
    }

    @Test
    fun aSwipeOfACardThatFitsGoesToTheNextOneAtOnce() = runAppTest(app(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-card")
        feed().performTouchInput { swipeUp() }
        settled("learn:ours")
        assertEquals("verb0:2:0", stored("place"))
    }

    @Test
    fun aCardThatDoesNotFitScrollsByTheWheelAndOnlyThenGoesOn() = onEachLongCard { tag ->
        val place = stored("place")
        val before = top(tag)
        feed().performMouseInput {
            moveTo(center)
            scroll(3f)
        }
        idle()
        assertEquals(place, stored("place"), "$tag: still this card")
        assertTrue(top(tag) < before, "$tag: it moved")

        feed().performMouseInput { scroll(-3f) }
        idle()
        assertEquals(before, top(tag), "$tag: and back")

        // To its very end, and that in itself does not go on: a flick of the wheel must not run through the next card.
        feed().performMouseInput { scroll(100_000f) }
        idle()
        feed().performMouseInput { scroll(1f) }
        idle()
        assertEquals(place, stored("place"), "$tag: the same flick ends there")

        feed().performMouseInput {
            advanceEventTime(1_000)
            scroll(1f)
        }
        idle()
        assertNotEquals(place, stored("place"), "$tag: the next notch goes on")
    }

    @Test
    fun aWheelThatKeepsTurningStaysAtTheEndOfTheCardUntilItPauses() = onEachLongCard { tag ->
        val place = stored("place")
        feed().performMouseInput {
            moveTo(center)
            scroll(100_000f)
        }
        idle()
        // A notch every 150 ms for three seconds: much longer than the pause, but none of them is one.
        repeat(20) {
            feed().performMouseInput {
                advanceEventTime(150)
                scroll(1f)
            }
            idle()
        }
        assertEquals(place, stored("place"), tag)

        feed().performMouseInput {
            advanceEventTime(500)
            scroll(1f)
        }
        idle()
        assertNotEquals(place, stored("place"), "$tag: after a pause it goes on")
    }

    @Test
    fun aWheelGoesThroughCardsThatFitOneByOneWithoutAPause() = runAppTest(app(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-card")
        repeat(4) {
            feed().performMouseInput {
                moveTo(center)
                advanceEventTime(450)
                scroll(1f)
            }
            idle()
        }
        assertEquals("verb0:5:0", stored("place"))
    }

    @Test
    fun theWheelUpFromTheTopOfACardGoesBack() = runAppTest(long(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-word")
        feed().performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        settled("learn:verb-card")
    }

    @Test
    fun aCardThatDoesNotFitScrollsByAMouseDragAndOnlyThenGoesOn() = onEachLongCard { tag ->
        val place = stored("place")
        val before = top(tag)
        feed().performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, -300f))
            release()
        }
        idle()
        assertEquals(place, stored("place"), "$tag: still this card")
        assertTrue(top(tag) < before, "$tag: it moved")

        feed().performMouseInput {
            press()
            moveBy(Offset(0f, 300f))
            release()
        }
        idle()
        assertEquals(before, top(tag), "$tag: and back")

        feed().performMouseInput {
            scroll(100_000f)
            moveTo(center + Offset(0f, 200f))
            press()
            moveBy(Offset(0f, -300f))
            release()
        }
        idle()
        assertNotEquals(place, stored("place"), "$tag: from its end a drag goes on")
    }

    @Test
    fun aCardThatDoesNotFitScrollsByTheArrowKeysBeforeTheyLeaveIt() = onEachLongCard { tag ->
        val place = stored("place")
        val before = top(tag)
        feed().performKeyInput { pressKey(Key.DirectionDown) }
        idle()
        assertEquals(place, stored("place"), "$tag: still this card")
        assertTrue(top(tag) < before, "$tag: it moved")

        feed().performKeyInput { pressKey(Key.DirectionUp) }
        idle()
        assertEquals(before, top(tag), "$tag: and back")
    }

    @Test
    fun aTapOnACardThatDoesNotFitStillGoesOn() = onEachLongCard { tag ->
        val place = stored("place")
        tap()
        assertNotEquals(place, stored("place"), tag)
    }

    @Test
    fun aPhraseThatDoesNotFitStillGoesAlongItsStepsAndUpAtItsEnd() = runAppTest(long(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        next()
        settled("learn:thought")
        feed().performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-200f, 0f))
            release()
        }
        settled("learn:english")
        assertEquals("verb0:2:2", stored("place"))
    }

    @Test
    fun aShortPhraseIsInTheMiddleAndAFingerStillGoesToTheNextCard() = runAppTest(long(), prepare = at("verb0:3:0")) {
        openFeed()
        settled("learn:ours")
        val page = node("learn:feed").getUnclippedBoundsInRoot()
        val text = node("learn:ours").getUnclippedBoundsInRoot()
        assertTrue(text.top > page.top + (page.bottom - page.top) / 4 && text.bottom < page.bottom - (page.bottom - page.top) / 4, "$text in $page")

        // Begun on the text itself, which scrolls if it has to and here has no reason to.
        feed().performTouchInput { swipeUp(startY = text.top.toPx() + 5f, endY = 0f) }
        settled("learn:verb-card")
        assertEquals("verb1:0:0", stored("place"))
    }

    @Test
    fun theLetterSPageScrollsByAFinger() = aboutALongVerb {
        val before = top("learn:about-word")
        node("learn:about-page").performTouchInput { swipeUp() }
        idle()
        assertTrue(top("learn:about-word") < before)
    }

    @Test
    fun theLetterSPageScrollsByTheWheel() = aboutALongVerb {
        val before = top("learn:about-word")
        node("learn:about-page").performMouseInput {
            moveTo(center)
            scroll(3f)
        }
        idle()
        assertTrue(top("learn:about-word") < before)
    }

    @Test
    fun theLetterSPageScrollsByAMouseDrag() = aboutALongVerb {
        val before = top("learn:about-word")
        node("learn:about-page").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, -300f))
            release()
        }
        idle()
        assertTrue(top("learn:about-word") < before)
    }

    @Test
    fun nothingOnTheLetterSPageReachesTheFeedUnderIt() = aboutALongVerb {
        // The strip beside the arrow, where the page has nothing of its own to press.
        node("learn:about-page").performTouchInput { click(Offset(width * 0.7f, 8f)) }
        idle()
        node("learn:about-page").performMouseInput {
            moveTo(Offset(width * 0.7f, 8f))
            scroll(1f)
        }
        idle()
        settled("learn:about-page")
        assertEquals("verb0:3:0", stored("place"))
    }

    private fun aboutALongVerb(check: AppTestScope<Unit>.() -> Unit) = runAppTest(long(), prepare = at("verb0:3:0")) {
        openFeed()
        settled("learn:ours")
        click("learn:about")
        settled("learn:about-page")
        check()
    }

    @Test
    fun theListIsWhereItWasAfterTheFeed() = runAppTest(
        app(count = 60),
        prepare = { it.storage.entries["chosen"] = List(40) { n -> "verb$n" }.joinToString(",") },
    ) {
        reach("learn:verb-verb30")
        val before = top("learn:verb-verb30")
        assertAbsent("learn:go")
        click("learn:verb-verb30")
        settled("learn:verb-card")
        click("learn:to-list")
        settled("learn:verb-verb30")
        assertEquals(before, top("learn:verb-verb30"))
    }

    @Test
    fun theSeenListIsWhereItWasAfterASecondLook() = runAppTest(app(count = 60), prepare = manySeen()) {
        openSeen()
        node("learn:seen-list").performScrollToNode(hasTestTag("learn:seen-verb-verb20"))
        idle()
        val before = top("learn:seen-verb-verb20")
        click("learn:seen-verb-verb20")
        settled("learn:verb-card")
        click("learn:to-list")
        settled("learn:seen-verb-verb20")
        assertEquals(before, top("learn:seen-verb-verb20"))
    }

    // The covers and the verb's card

    @Test
    fun aCoverSaysInBothLanguagesWhichTenseAndWhichPortionComesNext() = runAppTest(app()) {
        openFeed()
        settled("learn:verb-card")
        tap()
        settled("learn:tense-card")
        node("learn:tense-word").assertTextEquals("verb0")
        node("learn:tense-form").assertTextEquals("form 0")
        for ((strings, lang) in listOf(LearnStrings.Ru to "ru", LearnStrings.En to "en")) {
            node("learn:tense-voice-$lang").assertTextEquals(strings.voiceNames.getValue(Voice.Active))
            node("learn:tense-title-$lang").assertTextEquals(strings.aspectNames.getValue(Aspect.Simple))
            node("learn:tense-time-$lang").assertTextEquals(strings.timeNames.getValue(Time.Past))
            node("learn:tense-about-$lang").assertExists()
        }
        node("learn:tense-portion-ru").assertTextEquals("Порция 1 из 6 · 1 карточка")
        node("learn:tense-portion-en").assertTextEquals("Portion 1 of 6 · 1 card")

        tap()
        settled("learn:ours")
        node("learn:ours").assertTextEquals("ours verb0 t0")
        tap()
        tap()
        tap()
        settled("learn:tense-card")
        node("learn:tense-portion-ru").assertTextEquals("Порция 2 из 6 · 1 карточка")
    }

    @Test
    fun aCoverIsInBothLanguagesInEnglishToo() = runAppTest(
        app(),
        prepare = {
            it.locale.langTag.value = "en"
            at("verb0:1:0")(it)
        },
    ) {
        openFeed()
        settled("learn:tense-card")
        node("learn:tense-title-ru").assertTextEquals(LearnStrings.Ru.aspectNames.getValue(Aspect.Simple))
        node("learn:tense-title-en").assertTextEquals(LearnStrings.En.aspectNames.getValue(Aspect.Simple))
    }

    @Test
    fun aVerbsCardSaysWhatItIsAndItsFormsAndMeanings() = runAppTest(app()) {
        openFeed()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb0")
        node("learn:about-sound").assertTextEquals("[verb0-звук]")
        node("learn:about-meaning").assertTextEquals("meaning of verb0")
        node("learn:form-past-0").assertTextEquals("verb0ed")
        node("learn:form-past-1").assertTextEquals("verb0old")
        node("learn:about-notes-text").assertTextEquals("notes of verb0")
    }

    @Test
    fun aTenseThatDoesNotExistSaysWhyAndIsNoPortion() = runAppTest(
        LearnApp(base(12, closedPassive = true), RecordingSpeech()),
        prepare = at("verb0:13:0"),
    ) {
        openFeed()
        settled("learn:tense-card")
        node("learn:tense-closed").assertTextEquals("no such passive")
        for ((strings, lang) in listOf(LearnStrings.Ru to "ru", LearnStrings.En to "en")) {
            node("learn:tense-title-$lang").assertTextEquals(strings.voiceNames.getValue(Voice.Passive))
            node("learn:tense-no-$lang").assertTextEquals(strings.notPossible)
            assertAbsent("learn:tense-time-$lang")
            assertAbsent("learn:tense-portion-$lang")
            assertAbsent("learn:tense-about-$lang")
        }

        // There are no phrases to go through: next is the next verb.
        tap()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")
    }

    @Test
    fun theHintIsOnTheFirstCardOnly() = runAppTest(app()) {
        openFeed()
        settled("learn:hint")
        tap()
        settled("learn:tense-card")
        assertAbsent("learn:hint")
    }

    // A phrase

    @Test
    fun aPhraseGoesToHowToSayItToTheLeftAndIsSaidThere() {
        val speech = RecordingSpeech()
        runAppTest(app(speech), prepare = at("verb0:2:0")) {
            openFeed()
            settled("learn:ours")
            node("learn:ours").assertTextEquals("ours verb0 t0")
            node("learn:word").assertTextEquals("verb0")
            node("learn:word-sound").assertTextEquals("[verb0-звук]")
            node("learn:counter").assertTextEquals("3 / 13")
            assertEquals(emptyList(), speech.said)

            next()
            settled("learn:thought")
            node("learn:thought").assertTextEquals("thought verb0 t0")
            assertEquals(emptyList(), speech.said, "not a sound before its step")

            next()
            settled("learn:english")
            node("learn:english").assertTextEquals("english verb0 t0")
            node("learn:transcription").assertTextEquals("[транс]")
            assertEquals(listOf("english verb0 t0"), speech.said, "said as the step comes up")

            click("learn:say")
            assertEquals(listOf("english verb0 t0", "english verb0 t0"), speech.said)

            node("learn:feed").performTouchInput { swipeRight() }
            settled("learn:thought")
        }
    }

    @Test
    fun comingBackToTheStepIsNoReasonToSpeak() {
        val speech = RecordingSpeech()
        runAppTest(app(speech), prepare = at("verb3:2:2")) {
            openFeed()
            settled("learn:english")
            assertEquals(emptyList(), speech.said)
        }
    }

    @Test
    fun upIsTheNextCardAndTheSoundStopsAndTheStepIsKept() {
        val speech = RecordingSpeech(speakMillis = 60_000)
        runAppTest(app(speech), prepare = at("verb0:2:0")) {
            openFeed()
            settled("learn:ours")
            repeat(2) { next() }
            settled("learn:english")
            assertEquals(listOf("english verb0 t0"), speech.said)
            val stops = speech.stops

            feed().performTouchInput { swipeUp() }
            settled("learn:tense-card")
            assertTrue(speech.stops > stops, "the sound of the last card ends with it")

            // Back down, the phrase is where it was left.
            feed().performTouchInput { swipeDown() }
            settled("learn:card-2")
            settled("learn:english")
        }
    }

    @Test
    fun withoutASynthesizerThePhraseSaysSo() = runAppTest(app(RecordingSpeech(available = false)), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        repeat(2) { next() }
        settled("learn:no-speech")
        assertAbsent("learn:say")
    }

    // Tapping

    /** Below the middle of the screen, where the speaker is not. */
    private fun AppTestScope<Unit>.tapOnly() = feed().performTouchInput { click(percentOffset(0.5f, 0.75f)) }

    private fun AppTestScope<Unit>.tap() {
        tapOnly()
        idle()
    }

    @Test
    fun aTapGoesAlongThePhraseAndFromItsLastStepToTheNextCard() {
        val speech = RecordingSpeech()
        runAppTest(app(speech), prepare = at("verb0:2:0")) {
            openFeed()
            settled("learn:ours")
            tap()
            settled("learn:thought")
            tap()
            settled("learn:english")
            assertEquals(listOf("english verb0 t0"), speech.said)

            tap()
            settled("learn:tense-card")
            node("learn:tense-form").assertTextEquals("form 1")
            assertEquals("verb0:3:0", runtime.storage.entries["place"])

            tap()
            settled("learn:ours")
            node("learn:ours").assertTextEquals("ours verb0 t1")
        }
    }

    @Test
    fun aTapOnTheLastCardOfAVerbGoesToTheNextVerbsCard() = runAppTest(app(), prepare = at("verb0:12:2")) {
        openFeed()
        settled("learn:english")
        tap()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")
    }

    @Test
    fun theLeftButtonOfAMouseIsATapAndTheRightOneIsNot() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        feed().performMouseInput { click(percentOffset(0.5f, 0.75f)) }
        idle()
        settled("learn:thought")

        feed().performMouseInput { rightClick(percentOffset(0.5f, 0.75f)) }
        idle()
        settled("learn:thought")
    }

    @Test
    fun aMouseThatMovedIsNotAClick() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        // Further than a click wanders, not as far as a swipe.
        feed().performMouseInput {
            moveTo(Offset(300f, 500f))
            press()
            moveTo(Offset(330f, 500f))
            release()
        }
        idle()
        settled("learn:ours")
    }

    @Test
    fun theButtonsKeepTheirClicks() {
        val speech = RecordingSpeech()
        runAppTest(app(speech), prepare = at("verb0:2:2")) {
            openFeed()
            settled("learn:english")
            click("learn:say")
            idle()
            settled("learn:english")
            assertEquals(listOf("english verb0 t0"), speech.said)

            click("learn:play")
            click("learn:play")
            settled("learn:english")

            click("learn:to-list")
            settled("learn:list")
            assertEquals("verb0:2:2", runtime.storage.entries["place"], "the way out is not a step forward")
        }
    }

    @Test
    fun aTapReachesTheEndAndStopsThereAndTheEndLeadsToTheList() = runAppTest(app(), prepare = at("verb9:12:2")) {
        openFeed()
        settled("learn:english")
        tap()
        settled("learn:done")

        tap()
        settled("learn:done")

        click("learn:finish-list")
        settled("learn:list")
        assertEquals(null, runtime.storage.entries["place"], "from the end there is nothing to go on from")
    }

    @Test
    fun aQuickSecondTapGoesOnFromWhereTheFirstIsHeading() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        ui.mainClock.autoAdvance = false
        tapOnly()
        ui.mainClock.advanceTimeBy(120)
        tapOnly()
        ui.mainClock.autoAdvance = true
        idle()
        settled("learn:english")
    }

    @Test
    fun aQuickSecondTapOnTheNextPhraseIsItsFirstStep() = runAppTest(app(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-card")
        ui.mainClock.autoAdvance = false
        tapOnly()
        ui.mainClock.advanceTimeBy(120)
        tapOnly()
        ui.mainClock.autoAdvance = true
        idle()
        settled("learn:thought")
    }

    @Test
    fun theArrowKeysDoWhatSwipesDo() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        feed().performKeyInput { pressKey(Key.DirectionRight) }
        settled("learn:thought")
        feed().performKeyInput { pressKey(Key.DirectionDown) }
        settled("learn:tense-card")
        node("learn:tense-form").assertTextEquals("form 1")
    }

    @Test
    fun aMouseDragAndTheWheelDoWhatSwipesDo() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        feed().performMouseInput {
            moveTo(Offset(300f, 300f))
            press()
            moveTo(Offset(100f, 300f))
            release()
        }
        settled("learn:thought")
        feed().performMouseInput {
            moveTo(Offset(100f, 400f))
            press()
            moveTo(Offset(100f, 100f))
            release()
        }
        settled("learn:tense-card")
        node("learn:tense-form").assertTextEquals("form 1")

        feed().performMouseInput { scroll(1f) }
        settled("learn:ours")
        node("learn:ours").assertTextEquals("ours verb0 t1")
    }

    // The letter

    @Test
    fun theLetterOpensWhatThereIsToReadAndBackIsWhereItWas() = runAppTest(app(), prepare = at("verb2:2:1")) {
        openFeed()
        settled("learn:thought")
        click("learn:about")
        settled("learn:about-page")

        node("learn:about-word").assertTextEquals("verb2")
        node("learn:about-sound").assertTextEquals("[verb2-звук]")
        node("learn:about-meaning").assertTextEquals("meaning of verb2")
        node("learn:form-base-0").assertTextEquals("verb2")
        node("learn:form-past-0").assertTextEquals("verb2ed")
        node("learn:form-past-0-sound").assertTextEquals("[verb2ed-звук]")
        node("learn:form-past-1").assertTextEquals("verb2old")
        node("learn:form-participle-0").assertTextEquals("verb2en")
        node("learn:form-ing-0").assertTextEquals("verb2ing")
        node("learn:form-s-0").assertTextEquals("verb2s")
        node("learn:about-notes-text").assertTextEquals("notes of verb2")
        node("learn:about-signs").assertExists()

        click("learn:about-back")
        assertAbsent("learn:about-page")
        settled("learn:thought")
        node("learn:word").assertTextEquals("verb2")
        assertEquals("verb2:2:1", runtime.storage.entries["place"], "the letter is not a step forward")
        assertEquals(0, runtime.navigation.closed, "back from the page does not close the app")
    }

    @Test
    fun theFeedDoesNotMoveUnderThePage() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        click("learn:about")
        settled("learn:about-page")

        node("learn:about-page").performTouchInput { click(percentOffset(0.5f, 0.75f)) }
        idle()
        feed().performKeyInput { pressKey(Key.DirectionRight) }
        idle()
        feed().performKeyInput { pressKey(Key.DirectionDown) }
        idle()
        settled("learn:about-page")

        node("learn:about-back").performMouseInput { click() }
        idle()
        settled("learn:ours")
        node("learn:word").assertTextEquals("verb0")

        // And the keys are the feed's again.
        feed().performKeyInput { pressKey(Key.DirectionRight) }
        settled("learn:thought")
    }

    @Test
    fun aVerbWithNothingToReadStillHasItsCardAndItsPage() = runAppTest(
        LearnApp(base(12, described = false), RecordingSpeech()),
        prepare = at("verb0:2:0"),
    ) {
        openFeed()
        settled("learn:ours")
        assertAbsent("learn:word-sound")
        click("learn:about")
        settled("learn:about-page")
        node("learn:about-meaning").assertTextEquals("meaning of verb0")
        assertAbsent("learn:about-sound")
        assertAbsent("learn:about-forms")
        assertAbsent("learn:about-notes")
    }

    // Play

    private val stepMillis = StepSeconds * 1_000L
    private val introMillis = IntroSeconds * 1_000L

    /** Longer than any slide between two pages takes. */
    private val slideMillis = 1_000L

    private fun AppTestScope<Unit>.advance(millis: Long) {
        ui.mainClock.advanceTimeBy(millis)
        ui.waitForIdle()
    }

    @Test
    fun playGivesEachStepItsSecondsAndTheLastOneTheTimeOfItsSoundOnTop() {
        val speech = RecordingSpeech(speakMillis = 5_000L)
        runAppTest(app(speech), prepare = at("verb0:2:0")) {
            openFeed()
            settled("learn:ours")
            click("learn:play")
            ui.mainClock.autoAdvance = false

            advance(stepMillis - 500)
            node("learn:ours").assertExists()
            advance(500 + slideMillis)
            node("learn:thought").assertExists()
            assertEquals(emptyList(), speech.said)

            advance(stepMillis + slideMillis)
            node("learn:english").assertExists()
            assertEquals(listOf("english verb0 t0"), speech.said)

            // Both slides may have ended early, so this is 5 to 7 seconds into the step: past its own seconds,
            // and a step without a sound would have left by now.
            advance(5_000)
            node("learn:english").assertExists()

            // 10 to 12 seconds in: the sound and the seconds are over, and the next card has only just begun.
            advance(5_000)
            assertAbsent("learn:english")
            node("learn:tense-card").assertExists()
        }
    }

    @Test
    fun aCardToReadIsHeldLongerThanAStep() = runAppTest(app(), prepare = at("verb0:1:0")) {
        openFeed()
        settled("learn:tense-card")
        click("learn:play")
        ui.mainClock.autoAdvance = false

        advance(stepMillis + slideMillis)
        node("learn:tense-card").assertExists()
        advance(introMillis - stepMillis + slideMillis)
        node("learn:ours").assertExists()
    }

    @Test
    fun theSameButtonStopsItAndTheSoundWithIt() {
        val speech = RecordingSpeech(speakMillis = 60_000)
        runAppTest(app(speech), prepare = at("verb0:2:2")) {
            openFeed()
            settled("learn:english")
            click("learn:play")
            assertEquals(listOf("english verb0 t0"), speech.said, "pressed on the step, it is said at once")

            val stops = speech.stops
            click("learn:play")
            assertTrue(speech.stops > stops)

            ui.mainClock.autoAdvance = false
            advance(stepMillis * 10)
            node("learn:english").assertExists()
            assertEquals(listOf("english verb0 t0"), speech.said, "stopping is no reason to speak again")
        }
    }

    @Test
    fun thePageOverTheFeedHoldsPlayAndGoingBackGoesOn() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        click("learn:play")
        click("learn:about")
        settled("learn:about-page")
        ui.mainClock.autoAdvance = false
        advance(stepMillis * 3)
        ui.mainClock.autoAdvance = true

        click("learn:about-back")
        settled("learn:ours")
        node("learn:word").assertTextEquals("verb0")

        ui.mainClock.autoAdvance = false
        advance(stepMillis + slideMillis)
        node("learn:thought").assertExists()
    }

    @Test
    fun playStopsAtTheEnd() = runAppTest(app(), prepare = at("verb9:12:2")) {
        openFeed()
        settled("learn:english")
        click("learn:play")
        ui.mainClock.autoAdvance = false
        advance(stepMillis + slideMillis)
        node("learn:done").assertExists()
        assertAbsent("learn:play")

        advance(stepMillis * 3)
        node("learn:done").assertExists()
    }

    @Test
    fun playGoesOnWithoutASynthesizerToo() = runAppTest(app(RecordingSpeech(available = false)), prepare = at("verb0:2:2")) {
        openFeed()
        settled("learn:no-speech")
        click("learn:play")
        ui.mainClock.autoAdvance = false
        advance(stepMillis + slideMillis)
        node("learn:tense-card").assertExists()
    }

    // Keeping the place, and what can go wrong

    @Test
    fun itGoesOnWhereItWasLeft() = runAppTest(app(), prepare = at("verb7:4:2")) {
        openFeed()
        settled("learn:english")
        node("learn:counter").assertTextEquals("5 / 13")
        node("learn:word").assertTextEquals("verb7")
    }

    @Test
    fun thePlaceIsWrittenAsTheFeedComesToRestAndGoesOnFromThereWithoutARestart() = runAppTest(app(), prepare = at("verb0:2:0")) {
        openFeed()
        settled("learn:ours")
        next()
        settled("learn:thought")
        assertEquals("verb0:2:1", runtime.storage.entries["place"])

        click("learn:to-list")
        openFeed()
        settled("learn:thought")
    }

    @Test
    fun aPlaceInAVerbThatIsNoLongerChosenStartsFromTheTop() = runAppTest(
        app(),
        prepare = {
            it.storage.entries["place"] = "verb7:4:2"
            it.storage.entries["chosen"] = "verb1"
        },
    ) {
        openFeed()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb1")
    }

    @Test
    fun aPlaceFromAnEarlierVersionStartsFromTheTop() = runAppTest(app(), prepare = at("3:2")) {
        openFeed()
        settled("learn:verb-card")
        node("learn:about-word").assertTextEquals("verb0")
    }

    @Test
    fun theWayOutClosesTheAppAndTheSound() {
        val speech = RecordingSpeech()
        runAppTest(app(speech)) {
            settled("learn:list")
            click("learn:close")
            assertEquals(1, runtime.navigation.closed)
            assertTrue(speech.stops > 0)
        }
    }

    @Test
    fun anEmptyBaseSaysSo() = runAppTest(app(count = 0)) {
        settled("learn:empty")
    }

    @Test
    fun aBrokenFileCostsOnlyItsWord() = runAppTest(
        LearnApp(
            object : VerbSource {
                private val good = base(2)

                override suspend fun themes() = good.themes()

                override suspend fun index(theme: String) = listOf("verb0", "broken", "verb1")

                override suspend fun read(theme: String, word: String) =
                    if (word == "broken") "{ not json" else good.read(theme, word)
            },
            RecordingSpeech(),
        ),
    ) {
        settled("learn:list")
        node("learn:verb-verb0").assertExists()
        node("learn:verb-verb1").assertExists()
        assertAbsent("learn:verb-broken")
    }
}

class ProcessSpeechTest {
    private val dir = Files.createTempDirectory("english-speech").toFile()
    private val heard = File(dir, "heard")

    /** A synthesizer that writes down what it was asked to say, one argument to a line. */
    private val script = File(dir, "say.sh").apply {
        writeText("#!/bin/sh\nfor a in \"\$@\"; do echo \"\$a\"; done > '${heard.path}'\n")
        setExecutable(true)
    }

    private fun waitForHeard(): List<String> {
        repeat(100) {
            if (heard.isFile && heard.length() > 0) return heard.readLines()
            Thread.sleep(50)
        }
        error("Nothing was said")
    }

    @Test
    fun theSentenceGoesAsOneArgumentWithNoShellBetween() = runBlocking<Unit> {
        val tricky = "Don't \$(touch x); say \"it\""
        val speech = ProcessSpeech { text -> listOf(script.path, "-v", "en", text) }
        assertTrue(speech.available)
        speech.speak(tricky)
        assertEquals(listOf("-v", "en", tricky), waitForHeard())
        assertFalse(File("x").exists())
    }

    @Test
    fun speakingReturnsOnlyOnceItIsSaid() = runBlocking<Unit> {
        val speech = ProcessSpeech { text -> listOf(script.path, text) }
        speech.speak("done")
        assertEquals(listOf("done"), heard.readLines(), "no waiting needed: the program has finished")
    }

    @Test
    fun beingCancelledDoesNotWaitForTheEndOfTheSentence() = runBlocking<Unit> {
        val long = File(dir, "long.sh").apply {
            writeText("#!/bin/sh\nexec sleep 30\n")
            setExecutable(true)
        }
        val speech = ProcessSpeech { listOf(long.path) }
        val started = System.currentTimeMillis()
        withTimeoutOrNull(300) { speech.speak("a long sentence") }
        speech.stop()
        assertTrue(System.currentTimeMillis() - started < 5_000, "cut off, not waited for")
    }

    @Test
    fun nothingToRunMeansUnavailableAndSpeakingIsHarmless() = runBlocking<Unit> {
        val speech = ProcessSpeech { null }
        assertFalse(speech.available)
        speech.speak("anything")
        speech.stop()
    }

    @Test
    fun aMissingProgramIsNotACrash() = runBlocking<Unit> {
        val speech = ProcessSpeech { listOf(File(dir, "no-such-program").path, it) }
        speech.speak("anything")
        speech.stop()
    }
}

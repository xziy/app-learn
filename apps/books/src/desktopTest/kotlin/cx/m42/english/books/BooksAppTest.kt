package cx.m42.english.books

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.testing.AppContractTest
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The library's TCK: whatever this app grows into, it stays a well-behaved Superizer app. */
class BooksContractTest : AppContractTest(BooksApp())

/** A sentence of the test book with one noun, `dog` / «собаку» / «собака». */
private fun sentence(n: Int) = """{"ours":"Собака номер $n спит.","thought":"[Известная] собака номер $n спит.",
    "english":"The dog number $n sleeps.","transcription":"[ðэ дог н**а**мбэ ... сли:пс]",
    "nouns":[{"word":"dog","meaning":"собака","form":"dog","thought":"собака","ours":"Собака"},
             {"word":"number","meaning":"номер","form":"number","thought":"номер","ours":"номер","sense":"label"}]}"""

/** Two books: `dogs`, 30 paragraphs of two sentences, the last one not translated yet, and `empty`, which cannot be read. */
private class TwoBooks : BookSource {
    val reads = mutableListOf<String>()

    override suspend fun index() = listOf(
        BookInfo("dogs", "Собаки", "Автор", paragraphs = 30, sentences = 60, translated = 59),
        BookInfo("broken", "Сломанная", paragraphs = 1, sentences = 1, translated = 1),
    )

    override suspend fun read(slug: String): String {
        reads += slug
        if (slug == "broken") error("no such book")
        val paragraphs = (1..30).joinToString(",") { p ->
            val second = if (p == 30) """{"ours":"Последняя фраза.","thought":"","english":"","transcription":"","nouns":[]}""" else sentence(p * 10 + 2)
            """{"n":$p${if (p == 1) ""","heading":true""" else ""},"sentences":[${sentence(p * 10 + 1)},$second]}"""
        }
        return """{"book":1,"title":"Собаки","author":"Автор","transcription":true,"sentences":60,"translated":59,
            "paragraphs":[$paragraphs],
            "words":{"dog":{"transcription":"[дог]","countable":"yes","plural":[{"word":"dogs","transcription":"[догз]"}],
                     "senses":[{"ru":["собака"],"en":"an animal"}]},
                     "number":{"transcription":"[н**а**мбэ]","senses":[{"ru":["число"]},{"id":"label","ru":["номер"]}]}}}"""
    }
}

@OptIn(ExperimentalTestApi::class)
class BooksAppTest {

    private fun app(shelf: TwoBooks = TwoBooks()) = BooksApp(shelf)

    private fun AppTestScope<BooksConfig>.stored(key: String): String? = runtime.storage.entries[key]

    private fun AppTestScope<BooksConfig>.openDogs() {
        click("books:book-dogs")
        assertShown("books:reader")
    }

    /** A tap on the first noun of a text, where its layout puts it. */
    private fun AppTestScope<BooksConfig>.tapNoun(tag: String, word: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val at = layout.layoutInput.text.text.indexOf(word)
        assertTrue(at >= 0, "$word in ${layout.layoutInput.text.text}")
        val box = layout.getBoundingBox(at + 1)
        node(tag).performTouchInput { click(Offset(box.center.x, box.center.y)) }
        idle()
    }

    /** A tap on a text away from its nouns: at its last letter. */
    private fun AppTestScope<BooksConfig>.tapText(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val box = layout.getBoundingBox(layout.layoutInput.text.length - 2)
        node(tag).performTouchInput { click(Offset(box.center.x, box.center.y)) }
        idle()
    }

    @Test
    fun itAsksForTheWholeScreen() = runAppTest(app()) {
        assertEquals(AppChrome(fullscreen = true), instance.chrome.value)
    }

    @Test
    fun theShelfHasTheBooksWithWhatIsTranslatedAndRead() = runAppTest(app(), prepare = { it.storage.entries["place.dogs"] = "16:0" }) {
        assertShown("books:shelf")
        assertShown("books:book-dogs")
        assertShown("books:book-broken")
        // Inside the row, which is one button for a screen reader and has all its words.
        node("books:book-dogs").assertTextContains("Прочитано 50%")
        node("books:book-dogs").assertTextContains("60 фраз · переведено 59")
        node("books:book-broken").assertTextContains("Не начата")
    }

    @Test
    fun aBookOpensWithItsTitleAndItsSentencesInTheChosenLines() = runAppTest(app()) {
        openDogs()
        assertShown("books:title")
        assertShown("books:s-0-0-ours")
        assertShown("books:s-0-0-thought")
        assertShown("books:s-0-0-english")
        assertAbsent("books:s-0-0-transcription")
        assertEquals("dogs", stored("recent"))
    }

    @Test
    fun theLastReadBookComesFirstOnTheShelf() = runAppTest(app(), prepare = { it.storage.entries["recent"] = "broken" }) {
        val top = node("books:book-broken").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top < node("books:book-dogs").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun aBookThatCannotBeReadSaysSoAndTheArrowGoesBack() = runAppTest(app()) {
        click("books:book-broken")
        assertShown("books:cannot-open")
        click("books:back")
        assertShown("books:shelf")
    }

    @Test
    fun backGoesToTheShelfAndThePlaceIsKept() = runAppTest(app()) {
        openDogs()
        node("books:reader").performScrollToNode(hasTestTag("books:p-12"))
        idle()
        // Reading down took the bars away; a little way back up brings them back.
        assertAbsent("books:back")
        node("books:reader").performTouchInput { swipeDown(startY = centerY, endY = centerY + 120f) }
        idle()
        click("books:back")
        assertShown("books:shelf")
        val place = Place.decode(stored("place.dogs"))!!
        assertTrue(place.item >= 10, "$place")
        click("books:book-dogs")
        assertShown("books:p-12")
    }

    @Test
    fun aTapOnANounShowsWhatItMeansAndTheDictionarysWord() = runAppTest(app()) {
        openDogs()
        tapNoun("books:s-0-0-english", "dog")
        assertShown("books:word")
        node("books:word-word").assertTextEquals("dog")
        node("books:word-meaning").assertTextEquals("собака")
        node("books:word-sound").assertTextEquals("[дог]")
        node("books:word-thought").assertTextEquals("собака")
        node("books:word-ours").assertTextEquals("Собака")
        node("books:word-count").assertTextEquals("В книге: 59 раз")
        click("books:word-done")
        assertAbsent("books:word")
    }

    @Test
    fun aNounInTheThoughtOpensTheSameWordAndItsOtherSenses() = runAppTest(app()) {
        openDogs()
        tapNoun("books:s-0-0-thought", "номер")
        node("books:word-word").assertTextEquals("number")
        node("books:word-meaning").assertTextEquals("номер")
        // The main sense is the other one here.
        assertShown("books:word-sense-main")
        // Above the sheet, which covers the lower half.
        node("books:word-scrim").performTouchInput { click(Offset(centerX, 20f)) }
        idle()
        assertAbsent("books:word")
    }

    @Test
    fun aTapElsewhereOpensTheLinesThatAreOff() = runAppTest(app(), prepare = { it.storage.entries["lines"] = "english" }) {
        openDogs()
        // One line: the paragraph runs on.
        assertShown("books:flow-0")
        assertAbsent("books:s-0-0-english")
        tapText("books:flow-1")
        assertShown("books:open-1-1")
        assertShown("books:s-1-1-ours")
        assertShown("books:s-1-1-thought")
        assertShown("books:s-1-1-transcription")
        tapText("books:s-1-1-ours")
        assertAbsent("books:open-1-1")
    }

    @Test
    fun theLinesAreChosenAndKeptAndTheLastOneStays() = runAppTest(app()) {
        openDogs()
        click("books:settings-open")
        assertShown("books:settings")
        click("books:line-ours")
        click("books:line-thought")
        assertEquals("english", stored("lines"))
        // The last line cannot go.
        click("books:line-english")
        assertEquals("english", stored("lines"))
        click("books:line-transcription")
        assertEquals("english,transcription", stored("lines"))
        click("books:settings-done")
        assertShown("books:s-0-0-transcription")
        assertAbsent("books:s-0-0-ours")
    }

    @Test
    fun thePageAndTheTypeAreChosenAndKept() = runAppTest(app()) {
        openDogs()
        click("books:settings-open")
        click("books:paper-dark")
        assertEquals("dark", stored("paper"))
        click("books:font-up")
        click("books:font-up")
        node("books:font-size").assertTextEquals("${DefaultFont + 2 * FontStep}")
        assertEquals("${DefaultFont + 2 * FontStep}", stored("font"))
        click("books:sans")
        assertEquals("no", stored("serif"))
        click("books:nouns")
        assertEquals("no", stored("nouns"))
    }

    @Test
    fun storedSettingsAreThereFromTheStart() = runAppTest(
        app(),
        prepare = { it.storage.entries.putAll(mapOf("font" to "25", "paper" to "light", "lines" to "thought,english")) },
    ) {
        openDogs()
        assertShown("books:s-0-0-thought")
        assertAbsent("books:s-0-0-ours")
        click("books:settings-open")
        node("books:font-size").assertTextEquals("25")
    }

    @Test
    fun keysScrollTheBookAndChangeTheType() = runAppTest(app()) {
        openDogs()
        node("books:reader").performKeyInput { pressKey(Key.MoveEnd) }
        idle()
        assertShown("books:end")
        node("books:percent").assertTextEquals("100%")
        node("books:reader").performKeyInput { pressKey(Key.Equals) }
        idle()
        assertEquals("${DefaultFont + FontStep}", stored("font"))
        node("books:reader").performKeyInput { pressKey(Key.Escape) }
        idle()
        assertShown("books:shelf")
    }

    @Test
    fun aSentenceNotTranslatedYetHasItsRussianAndSaysSo() = runAppTest(app(), prepare = { it.storage.entries["place.dogs"] = "30:0" }) {
        openDogs()
        assertShown("books:s-29-1-ours")
        assertAbsent("books:s-29-1-english")
    }

    @Test
    fun theContentsGoToAChapter() = runAppTest(app(), prepare = { it.storage.entries["place.dogs"] = "20:0" }) {
        openDogs()
        click("books:contents-open")
        click("books:chapter-0")
        assertAbsent("books:contents")
        assertShown("books:p-0")
    }
}

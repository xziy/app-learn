package cx.m42.english

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cx.m42.superizer.Superizer
import cx.m42.superizer.host.storage.PrefsStorage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The host in its own window size, on the base of words the app really carries: Home opens the list of
 * verbs, the list the feed, the feed has the whole window, and the way out leads back. Along the way the main states land as PNGs in
 * `composeApp/build/screenshots`, for a look.
 */
@OptIn(ExperimentalTestApi::class)
class EnglishHostTest {

    private val out = File("build/screenshots").apply { mkdirs() }
    private lateinit var storage: File

    /** A fresh directory each time: never the developer's own Home and language. */
    @BeforeTest
    fun isolateStorage() {
        storage = Files.createTempDirectory("english-test").toFile()
        PrefsStorage.useDirectory(storage)
    }

    private fun host(): Superizer =
        buildEnglishHost(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), debug = true)
            .also { it.settings.chooseLanguage("ru") }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    private fun ComposeUiTest.waitFor(tag: String) {
        waitUntil(timeoutMillis = 10_000L) { runCatching { onNodeWithTag(tag).assertExists() }.isSuccess }
        waitForIdle()
    }

    /** A tap on the first noun of a text, if it has one: where its link is, from the text's own layout. */
    private fun ComposeUiTest.tapFirstWord(tag: String): Boolean {
        val node = onNodeWithTag(tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.firstOrNull() ?: return false
        val text = layout.layoutInput.text
        val link = text.getLinkAnnotations(0, text.length).firstOrNull() ?: return false
        val at = layout.getBoundingBox((link.start + link.end) / 2).center
        node.performTouchInput { click(at) }
        return true
    }

    private fun ComposeUiTest.swipeStep() = onNodeWithTag("learn:feed").performTouchInput { swipeLeft() }

    private fun ComposeUiTest.swipeCard() = onNodeWithTag("learn:feed").performTouchInput { swipeUp() }

    /** A tap rather than a swipe where a card may have more to scroll than the window holds. */
    private fun ComposeUiTest.tap() = onNodeWithTag("learn:feed").performTouchInput { click(percentOffset(0.5f, 0.75f)) }

    @Test
    fun theLanguageOpensOnItsVerbsAndTheFeedHasTheWholeWindow() = runDesktopComposeUiTest(width = 420, height = 820) {
        val superizer = host()
        setContent { English(superizer) }

        waitFor("home:tile-learn")
        shot("01-home")

        onNodeWithTag("home:tile-learn").performClick()
        waitFor("learn:list")
        shot("02-chosen-verbs")
        onNodeWithContentDescription("Открыть меню").assertDoesNotExist()

        onNodeWithTag("learn:go").performClick()
        waitFor("learn:verb-card")
        shot("03-the-verb")

        val window = onRoot().getUnclippedBoundsInRoot()
        val feed = onNodeWithTag("learn:feed").getUnclippedBoundsInRoot()
        assertEquals(window.width, feed.width)
        assertEquals(window.height, feed.height)

        tap()
        waitFor("learn:tense-card")
        shot("04-the-tense")

        tap()
        waitFor("learn:ours")
        shot("05-how-we-think")

        swipeStep()
        waitFor("learn:thought")
        shot("06-how-the-english-think")

        swipeStep()
        waitFor("learn:english")
        shot("07-how-to-say-it")

        onNodeWithTag("learn:about").performClick()
        waitFor("learn:about-page")
        shot("08-about-the-verb")
        onNodeWithTag("learn:about-back").performClick()
        waitFor("learn:card-2")

        onNodeWithTag("learn:form").performClick()
        waitFor("learn:rule-page")
        shot("08b-the-rule")
        onNodeWithTag("learn:rule-back").performClick()
        waitFor("learn:card-2")

        // A noun of the phrase opens its page over the feed, and the feed is where it was on the way back.
        if (tapFirstWord("learn:english")) {
            waitFor("learn:word-page")
            shot("08c-the-word")
            onNodeWithTag("learn:word-back").performClick()
            waitFor("learn:card-2")
        }

        swipeCard()
        waitFor("learn:card-3")
        shot("09-next-phrase")

        // The place went into the host's own file, under this app's name.
        val settings = File(storage, "settings.properties").readText()
        assertTrue(Regex("""app\.learn\.place=[a-z]+\\:3\\:0""") in settings, settings)

        onNodeWithTag("learn:to-list").performClick()
        waitFor("learn:list")

        // The words of the English seen so far, in the dictionary.
        onNodeWithTag("learn:list").performScrollToNode(hasTestTag("learn:dict-open"))
        onNodeWithTag("learn:dict-open").performClick()
        waitFor("learn:dictionary")
        shot("10-dictionary")
        onNodeWithTag("learn:dict-back").performClick()
        waitFor("learn:list")
        onNodeWithTag("learn:list").performScrollToNode(hasTestTag("learn:theme-yachting"))

        // Another theme: its own round of its own verbs, and its own feed from the top.
        onNodeWithTag("learn:theme-yachting").performClick()
        waitUntil(timeoutMillis = 10_000L) { "app.learn.chosen.yachting=" in File(storage, "settings.properties").readText() }
        waitFor("learn:go")
        shot("10-another-theme")
        assertTrue("app.learn.theme=yachting" in File(storage, "settings.properties").readText())

        onNodeWithTag("learn:go").performClick()
        waitFor("learn:verb-card")
        tap()
        waitFor("learn:tense-card")
        shot("11-the-tense-of-another-theme")
        onNodeWithTag("learn:to-list").performClick()
        waitFor("learn:list")

        onNodeWithTag("learn:close").performClick()
        waitFor("home:tile-learn")
        onNodeWithContentDescription("Открыть меню").assertExists()
    }

    /**
     * The list of the real base in the host's real shell, where nothing around it may take the gestures away: all of
     * it can be reached by a finger, the wheel and a mouse's drag, and the seen ones have their own page.
     */
    @Test
    fun theListScrollsInTheHostAndTheSeenOnesHaveTheirPage() = runDesktopComposeUiTest(width = 420, height = 820) {
        val superizer = host()
        setContent { English(superizer) }
        waitFor("home:tile-learn")
        onNodeWithTag("home:tile-learn").performClick()
        waitFor("learn:go")

        onNodeWithTag("learn:list").performTouchInput { swipeUp() }
        waitForIdle()
        onNodeWithTag("learn:go").assertDoesNotExist()
        shot("14-the-list-scrolled")
        onNodeWithTag("learn:list").performTouchInput { swipeDown() }
        waitForIdle()
        onNodeWithTag("learn:go").assertExists()

        onNodeWithTag("learn:list").performMouseInput {
            moveTo(center)
            scroll(15f)
        }
        waitForIdle()
        onNodeWithTag("learn:go").assertDoesNotExist()
        onNodeWithTag("learn:list").performMouseInput { scroll(-15f) }
        waitForIdle()
        onNodeWithTag("learn:go").assertExists()

        onNodeWithTag("learn:list").performMouseInput {
            moveTo(Offset(center.x, height - 10f))
            press()
            repeat(8) { moveBy(Offset(0f, -70f)) }
            release()
        }
        waitForIdle()
        onNodeWithTag("learn:go").assertDoesNotExist()

        onNodeWithTag("learn:list").performScrollToNode(hasTestTag("learn:seen-open"))
        waitForIdle()
        shot("15-the-way-to-the-seen-ones")
        onNodeWithTag("learn:seen-open").performClick()
        waitFor("learn:seen-none")
        shot("16-nothing-seen-yet")
    }

    /** The longest of the covers on the smallest of the phones: all of it can be reached, and a tap still goes on. */
    @Test
    fun aCoverThatDoesNotFitAPhoneScrolls() = runDesktopComposeUiTest(width = 320, height = 568) {
        val superizer = host()
        setContent { English(superizer) }

        waitFor("home:tile-learn")
        onNodeWithTag("home:tile-learn").performClick()
        waitFor("learn:list")
        onNodeWithTag("learn:go").performClick()
        waitFor("learn:verb-card")
        tap()
        waitFor("learn:tense-card")
        shot("12-a-cover-on-a-small-phone")

        onNodeWithTag("learn:tense-about-en").performScrollTo().assertIsDisplayed()
        shot("13-the-end-of-that-cover")

        tap()
        waitFor("learn:ours")
    }
}

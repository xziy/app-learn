package cx.m42.english.learn

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.FakeAppRuntime
import cx.m42.superizer.testing.runAppTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingSharing(override val viaMenu: Boolean = true, private val works: Boolean = true) : Sharing {
    val shared = mutableListOf<String>()

    override fun share(text: String): Boolean {
        shared += text
        return works
    }
}

private object Silent : Speech {
    override val available = true

    override suspend fun speak(text: String) = Unit

    override fun stop() = Unit
}

/** The real `abandon`, from the base the app carries. */
private val abandon: Verb by lazy { parseVerb(File("src/commonMain/composeResources/files/verbs/general/abandon.json").readText()) }

/** Its pages: 2 is the first phrase, `Yesterday the sailors abandoned the ship.`, 4 the first question. */
private fun abandonAt(page: Int) = buildPages(listOf(abandon))[page] as FeedPage.Phrase

class AskPromptTest {
    @Test
    fun thePromptAsksWhyAndSaysWhatThePhraseIs() {
        assertEquals(
            """
            Объясни мне, почему это английское предложение построено именно так.

            Предложение: Did you abandon your plan?
            По-русски: Ты отказался от своего плана?
            Как думает англичанин, слово в слово: [Сделал] ты бросать твой план?
            В квадратных скобках — слова, которых по-русски не говорят, а по-английски они есть.
            Произношение русскими буквами: [дид ю: эбÆндэн йо: плæн] (заглавная буква — ударный звук)

            Глагол: abandon — покидать, бросать; отказываться (от плана, надежды); прерывать (матч)
            Его формы: abandon, abandoned, abandoned, abandoning, abandons
            Время: Тип 1 · Прошедшее · Вопрос (Актив · Past Simple)
            Схема: Did + кто + V + …?, где V — 1-я форма, как в словаре: abandon
            Существительные: plan — план

            Разбери предложение подробно: почему здесь это время, такой порядок слов и такая форма глагола, зачем каждое служебное слово, артикли и предлоги. Чем оно отличается от того, как сказали бы по-русски, и где тут легко ошибиться? Приведи 2–3 похожих предложения с переводом.
            """.trimIndent(),
            askPrompt(abandonAt(4), Theme(DefaultTheme, "Общая тема"), LearnStrings.Ru),
        )
    }

    @Test
    fun aNounWrittenOtherwiseHasItsDictionaryFormAndAThemeOtherThanTheGeneralOneIsNamed() {
        val prompt = askPrompt(abandonAt(2), Theme("yachting", "Яхтинг: паруса и снасти"), LearnStrings.Ru)
        assertTrue("Схема: кто + V2 + …, где V2 — 2-я форма, прошедшее время: abandoned\n" in prompt, prompt)
        assertTrue("Существительные: sailors (sailor) — моряк; ship — корабль\n" in prompt, prompt)
        assertTrue("Тема примеров: Яхтинг\n" in prompt, prompt)
    }

    @Test
    fun inEnglishThePromptIsEnglishAndTheThoughtStaysRussian() {
        val prompt = askPrompt(abandonAt(4), null, LearnStrings.En)
        assertTrue(prompt.startsWith("Explain to me why this English sentence is built the way it is.\n\nSentence: Did you abandon your plan?\n"), prompt)
        assertTrue("How the English think, word for word in Russian: [Сделал] ты бросать твой план?\n" in prompt, prompt)
        assertTrue("Tense: Type 1 · Past · Question (Active · Past Simple)\n" in prompt, prompt)
        assertTrue("Pattern: Did + who + V + …?, where V — 1st form, as in a dictionary: abandon\n" in prompt, prompt)
    }

    @Test
    fun aThoughtWithoutBracketsIsNotExplainedAndStressIsACapital() {
        val prompt = askPrompt(abandonAt(2), null, LearnStrings.Ru)
        assertEquals(
            "Как думает англичанин, слово в слово: Вчера [известные] моряки покинули [известное] судно.\n",
            prompt.lines().first { it.startsWith("Как думает") } + "\n",
        )
        assertEquals("[йЭстэдэй экскЮ:з]", plainStress("[й**э**стэдэй экск**ю:**з]"))
    }
}

@OptIn(ExperimentalTestApi::class)
class AskPageTest {
    private fun app(sharing: Sharing) = LearnApp(
        object : VerbSource {
            override suspend fun themes() = listOf(Theme(DefaultTheme, "Общая тема: всё подряд"))

            override suspend fun index(theme: String) = listOf("abandon")

            override suspend fun read(theme: String, word: String) =
                File("src/commonMain/composeResources/files/verbs/general/$word.json").readText()
        },
        Silent,
        shuffle = { it },
        sharing = sharing,
    )

    private fun at(place: String): (FakeAppRuntime) -> Unit = {
        it.storage.entries["place"] = place
        it.storage.entries["chosen"] = "abandon"
    }

    private fun AppTestScope<Unit>.settled(tag: String) {
        ui.waitUntil(timeoutMillis = 5_000L) { runCatching { node(tag).assertExists() }.isSuccess }
        idle()
    }

    private fun AppTestScope<Unit>.openFeed() {
        settled("learn:go")
        click("learn:go")
    }

    /** What is below the field may be below the screen too. */
    private fun AppTestScope<Unit>.press(tag: String) {
        node(tag).performScrollTo()
        click(tag)
    }

    private fun AppTestScope<Unit>.prompt(): String =
        node("learn:ask-prompt").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** The button of the phrase on [page], not of the one above it that may still be composed. */
    private fun AppTestScope<Unit>.ask(page: Int) {
        ui.onNode(hasTestTag("learn:ask") and hasAnyAncestor(hasTestTag("learn:card-$page"))).performClick()
        settled("learn:ask-page")
    }

    @Test
    fun aPhraseIsAskedAboutWithAPromptThatCanBeEditedBeforeItIsShared() {
        val sharing = RecordingSharing()
        runAppTest(app(sharing), prepare = at("abandon:4:1")) {
            openFeed()
            settled("learn:thought")
            ask(4)

            node("learn:ask-sentence").assertTextEquals("Did you abandon your plan?")
            val written = askPrompt(abandonAt(4), Theme(DefaultTheme, "Общая тема: всё подряд"), LearnStrings.Ru)
            assertEquals(written, prompt())
            node("learn:ask-send").assertTextEquals("Поделиться")
            node("learn:ask-how").assertTextEquals("Откроется меню «Поделиться»: выберите ChatGPT, мессенджер или другое приложение")
            assertAbsent("learn:ask-reset")

            press("learn:ask-send")
            assertEquals(listOf(written), sharing.shared, "the menu gets the prompt as it stands")
            assertAbsent("learn:ask-done")

            node("learn:ask-prompt").performTextReplacement("Почему здесь did, а глагол без -ed?")
            idle()
            press("learn:ask-send")
            assertEquals("Почему здесь did, а глагол без -ed?", sharing.shared.last(), "what is edited is what goes")

            press("learn:ask-reset")
            assertEquals(written, prompt())
            assertAbsent("learn:ask-reset")

            click("learn:ask-back")
            assertAbsent("learn:ask-page")
            settled("learn:thought")
            assertEquals("abandon:4:1", runtime.storage.entries["place"], "asking is not a step forward")
            assertEquals(0, runtime.navigation.closed, "back from the page does not close the app")
        }
    }

    @Test
    fun everyStepOfAPhraseHasTheButton() = runAppTest(app(RecordingSharing()), prepare = at("abandon:2:0")) {
        openFeed()
        settled("learn:ours")
        repeat(3) { step ->
            ui.onNode(hasTestTag("learn:ask") and hasAnyAncestor(hasTestTag("learn:card-2"))).assertExists()
            if (step < 2) {
                node("learn:feed").performTouchInput { swipeLeft() }
                idle()
            }
        }
        settled("learn:english")
    }

    @Test
    fun whereThereIsNoMenuTheTextIsCopiedAndSaysSo() = runAppTest(app(RecordingSharing(viaMenu = false)), prepare = at("abandon:2:2")) {
        openFeed()
        settled("learn:english")
        ask(2)
        node("learn:ask-send").assertTextEquals("Скопировать")
        node("learn:ask-how").assertTextEquals(
            "Здесь нет меню «Поделиться», поэтому текст копируется: вставьте его в ChatGPT или в мессенджер",
        )
        press("learn:ask-send")
        node("learn:ask-done").assertTextEquals("Скопировано")
    }

    @Test
    fun whatCouldNotBeSharedSaysSo() = runAppTest(app(RecordingSharing(works = false)), prepare = at("abandon:2:2")) {
        openFeed()
        settled("learn:english")
        ask(2)
        press("learn:ask-send")
        node("learn:ask-done").assertTextEquals("Не получилось. Выделите текст и скопируйте его сами")
    }
}

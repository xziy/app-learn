package cx.m42.english.learn

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** How many verbs are drawn at random for one round of the feed. */
internal const val WordsAtOnce: Int = 5

/** How long play keeps a step of a phrase on screen, not counting the time its sound takes. */
internal const val StepSeconds: Int = 4

/** How long play keeps a card that is there to be read: a verb's or a tense's. */
internal const val IntroSeconds: Int = 8

/** What one phrase goes through, swiped left and right. The last one is also said aloud. */
internal enum class Step { Ours, Thought, English }

/**
 * The phrases of one tense, as its cover announces them: the [number]-th portion of a verb's [total], of [cards]
 * cards. A tense that does not exist has no cards, so it is no portion and is not counted.
 */
internal data class Portion(val number: Int, val total: Int, val cards: Int)

/** One page of the feed, swiped up and down. */
internal sealed interface FeedPage {
    val verb: Verb

    /** Before the first tense of a verb: what this verb is. */
    data class VerbCard(override val verb: Verb) : FeedPage

    /** The cover of a tense, before its phrases: which tense this is and which portion of cards comes next. */
    data class TenseCard(override val verb: Verb, val tense: Tense, val portion: Portion?) : FeedPage

    /** One sentence, with its steps. */
    data class Phrase(override val verb: Verb, val tense: Tense, val sentence: Sentence) : FeedPage
}

/** The chosen verbs one after another, each through all of its tenses. */
internal fun buildPages(verbs: List<Verb>): List<FeedPage> = buildList {
    for (verb in verbs) {
        add(FeedPage.VerbCard(verb))
        val total = verb.tenses.count { it.sentences.isNotEmpty() }
        var number = 0
        for (tense in verb.tenses) {
            val cards = tense.sentences.size
            add(FeedPage.TenseCard(verb, tense, if (cards > 0) Portion(++number, total, cards) else null))
            tense.sentences.forEach { add(FeedPage.Phrase(verb, tense, it)) }
        }
    }
}

private val DarkBase = Color(0xFF14151B)

/** How much of a verb's own colour goes into the dark: enough to tell verbs apart, and white stays readable. */
private const val VerbTint = 0.25f

/** The background of a verb: dark, never black, a muted colour of its own that stays the same every time. */
internal fun verbColor(word: String): Color {
    val hue = word.hashCode().mod(360).toFloat()
    return lerp(DarkBase, Color.hsv(hue, saturation = 0.6f, value = 0.75f), VerbTint)
}

private val Stress = Regex("""\*\*(.+?)\*\*""")

/** `[экс**э**пт]` as text with the stressed vowel set off. */
internal fun stressed(transcription: String, stress: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (match in Stress.findAll(transcription)) {
        append(transcription.substring(at, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = stress)) { append(match.groupValues[1]) }
        at = match.range.last + 1
    }
    append(transcription.substring(at))
}

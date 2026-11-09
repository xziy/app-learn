package cx.m42.english.learn

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * A noun of a phrase as a link to its page. The ones not known yet are underlined, the known ones only under a mouse.
 * A link takes its press for itself, so a tap on a word opens it and a tap anywhere else still goes forward (D10).
 */
private fun wordLink(use: NounUse, known: Boolean, onWord: (NounUse) -> Unit) = LinkAnnotation.Clickable(
    tag = use.key,
    styles = TextLinkStyles(
        style = SpanStyle(textDecoration = if (known) TextDecoration.None else TextDecoration.Underline),
        hoveredStyle = SpanStyle(textDecoration = TextDecoration.Underline),
    ),
    linkInteractionListener = { onWord(use) },
)

/** The English of a phrase with each of its nouns a link. */
internal fun linkedPhrase(text: String, marks: List<Mark>, known: Set<String>, onWord: (NounUse) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text)
        marks.forEach { addLink(wordLink(it.use, it.use.key in known, onWord), it.start, it.end) }
    }

/** Its transcription with the stressed vowels set off, and the same nouns linked over the words they take up there. */
internal fun linkedSound(
    sentence: Sentence,
    marks: List<Mark>,
    known: Set<String>,
    stress: Color,
    onWord: (NounUse) -> Unit,
): AnnotatedString {
    val sounds = soundMarks(sentence.english, sentence.transcription, marks)
    if (sounds.isEmpty()) return stressed(sentence.transcription, stress)
    val words = sentence.transcription.trim().removePrefix("[").removeSuffix("]").split(' ').filter { it.isNotEmpty() }
    val starts = IntArray(words.size)
    val ends = IntArray(words.size)
    return buildAnnotatedString {
        append("[")
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            starts[index] = length
            append(stressed(word, stress))
            ends[index] = length
        }
        append("]")
        sounds.forEach { (range, use) -> addLink(wordLink(use, use.key in known, onWord), starts[range.first], ends[range.last]) }
    }
}

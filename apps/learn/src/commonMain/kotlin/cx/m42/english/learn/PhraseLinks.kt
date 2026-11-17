package cx.m42.english.learn

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A phrase with its links, and where its dotted lines go: under the service words not known yet, from the first
 * character of each to before its end.
 */
internal class Linked(val text: AnnotatedString, val dotted: List<IntRange>)

/** The star over an irregular verb, as a power is written. */
internal const val IrregularStar: String = "*"

private val StarStyle = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.6.em)

/**
 * A word of a phrase as a link to its page. Nouns and irregular verbs not known yet are underlined, service words not
 * known yet are dotted under, by [LinkedText]; the known ones only under a mouse. A link takes its press for itself,
 * so a tap on a word opens it and a tap anywhere else still goes forward (D10).
 */
private fun wordLink(use: WordLink, known: Boolean, onWord: (WordLink) -> Unit) = LinkAnnotation.Clickable(
    tag = use.key,
    styles = TextLinkStyles(
        style = SpanStyle(textDecoration = if (known || use is ServiceLink) TextDecoration.None else TextDecoration.Underline),
        hoveredStyle = SpanStyle(textDecoration = TextDecoration.Underline),
    ),
    linkInteractionListener = { onWord(use) },
)

/** Puts the link of [use] over what was appended since [start], and after it the star if it is an irregular verb. */
private fun AnnotatedString.Builder.linkSince(start: Int, use: WordLink, known: Set<String>, dotted: MutableList<IntRange>, onWord: (WordLink) -> Unit) {
    val isKnown = use.key in known
    addLink(wordLink(use, isKnown, onWord), start, length)
    if (use is ServiceLink && !isKnown) dotted += start until length
    // Outside the link, so the line under the word does not run on under the star.
    if (use is VerbLink) withStyle(StarStyle) { append(IrregularStar) }
}

/** The English of a phrase with each of its [marks] a link. */
internal fun linkedPhrase(text: String, marks: List<Mark>, known: Set<String>, onWord: (WordLink) -> Unit): Linked {
    val dotted = mutableListOf<IntRange>()
    val annotated = buildAnnotatedString {
        var at = 0
        for (mark in marks.sortedBy { it.start }) {
            if (mark.start < at || mark.end > text.length) continue
            append(text, at, mark.start)
            val start = length
            append(text, mark.start, mark.end)
            linkSince(start, mark.use, known, dotted, onWord)
            at = mark.end
        }
        append(text, at, text.length)
    }
    return Linked(annotated, dotted)
}

/** Its transcription with the stressed vowels set off, and the same links over the words they take up there. */
internal fun linkedSound(
    sentence: Sentence,
    marks: List<Mark>,
    known: Set<String>,
    stress: Color,
    onWord: (WordLink) -> Unit,
): Linked {
    val sounds = soundMarks(sentence.english, sentence.transcription, marks)
    if (sounds.isEmpty()) return Linked(stressed(sentence.transcription, stress), emptyList())
    val words = sentence.transcription.trim().removePrefix("[").removeSuffix("]").split(' ').filter { it.isNotEmpty() }
    val byLast = sounds.associateBy { it.first.last }
    val dotted = mutableListOf<IntRange>()
    val annotated = buildAnnotatedString {
        append("[")
        var start = 0
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            if (sounds.any { it.first.first == index }) start = length
            append(stressed(word, stress))
            byLast[index]?.let { (_, use) -> linkSince(start, use, known, dotted, onWord) }
        }
        append("]")
    }
    return Linked(annotated, dotted)
}

/**
 * [linked] with its dotted lines drawn under it. Text decoration has only a solid line, so these are drawn here, at
 * about where an underline would be, thin and dimmer than the text.
 */
@Composable
internal fun LinkedText(linked: Linked, style: TextStyle, dots: Color, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = linked.text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val length = result.layoutInput.text.length
            val stroke = 1.5.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.5.dp.toPx()))
            val size = result.layoutInput.style.fontSize
            val below = if (size.isSp) maxOf(stroke * 2, size.toPx() * 0.12f) else stroke * 2
            for (range in linked.dotted) {
                if (range.isEmpty() || range.last >= length) continue
                // A word does not break across lines, but where it would, each line gets its own part.
                for (line in result.getLineForOffset(range.first)..result.getLineForOffset(range.last)) {
                    val first = maxOf(range.first, result.getLineStart(line))
                    val last = minOf(range.last, result.getLineEnd(line, visibleEnd = true) - 1)
                    if (first > last) continue
                    val y = result.getLineBaseline(line) + below
                    drawLine(
                        color = dots,
                        start = Offset(result.getBoundingBox(first).left, y),
                        end = Offset(result.getBoundingBox(last).right, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = dash,
                    )
                }
            }
        },
    )
}

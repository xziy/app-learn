package cx.m42.english.books

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** How each line looks at the person's size and type. */
internal class ReaderStyle(settings: ReaderSettings, private val colors: PaperColors) {
    private val family = if (settings.serif) FontFamily.Serif else FontFamily.SansSerif
    private val size = settings.font

    fun of(line: Line, heading: Boolean = false): TextStyle {
        val scale = when (line) {
            Line.Ours, Line.English -> 1f
            Line.Thought -> 0.94f
            Line.Transcription -> 0.8f
        } * if (heading) 1.3f else 1f
        return TextStyle(
            fontFamily = if (line == Line.Transcription) FontFamily.SansSerif else family,
            fontSize = (size * scale).sp,
            lineHeight = (size * scale * 1.45f).sp,
            color = when (line) {
                Line.Thought -> colors.thought
                Line.Transcription -> colors.muted
                else -> colors.text
            },
            fontStyle = if (line == Line.Thought) FontStyle.Italic else FontStyle.Normal,
            fontWeight = when {
                heading -> FontWeight.Bold
                line == Line.English -> FontWeight.Medium
                else -> FontWeight.Normal
            },
        )
    }

    /** A Russian sentence standing in for one not put the English way yet. */
    val untranslated: SpanStyle get() = SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic)
}

/** A noun set off in a line of text: where it is, and which noun of which sentence of the paragraph. */
internal data class NounHit(val start: Int, val end: Int, val sentence: Int, val noun: Int)

/** A line or a paragraph as it is drawn, with the places of its nouns and of its sentences in it. */
internal data class Built(val text: AnnotatedString, val hits: List<NounHit>, val sentences: List<IntRange> = emptyList()) {
    fun sentenceAt(offset: Int): Int? = sentences.indexOfLast { it.first <= offset }.takeIf { it >= 0 }
}

private fun nounStyle(colors: PaperColors, noun: Int) = SpanStyle(color = colors.noun(noun), fontWeight = FontWeight.SemiBold)

/** Appends one line of a sentence with its nouns set off, and says where they are. */
private fun AnnotatedString.Builder.addLine(
    sentence: Sentence,
    index: Int,
    line: Line,
    colors: PaperColors,
    nouns: Boolean,
    hits: MutableList<NounHit>,
) {
    if (line == Line.Transcription) {
        val words = soundWords(sentence.transcription)
        val sayers = if (!nouns) emptyMap() else soundNouns(
            sentence.english,
            sentence.transcription,
            spans(sentence.english, sentence.nouns, Line.English),
        )
        append("[")
        words.forEachIndexed { at, word ->
            if (at > 0) append(" ")
            val start = length
            sounds(word).forEach { sound ->
                if (sound.stressed) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(sound.text) } else append(sound.text)
            }
            sayers[at]?.let { noun ->
                addStyle(nounStyle(colors, noun), start, length)
                hits += NounHit(start, length, index, noun)
            }
        }
        append("]")
        return
    }
    val base = length
    val text = sentence.text(line)
    append(text)
    if (!nouns) return
    spans(text, sentence.nouns, line).forEach { span ->
        addStyle(nounStyle(colors, span.noun), base + span.start, base + span.end)
        hits += NounHit(base + span.start, base + span.end, index, span.noun)
    }
}

/** One line of one sentence. */
internal fun buildLine(sentence: Sentence, line: Line, colors: PaperColors, nouns: Boolean): Built {
    val hits = mutableListOf<NounHit>()
    val text = buildAnnotatedString { addLine(sentence, 0, line, colors, nouns, hits) }
    return Built(text, hits)
}

/**
 * A paragraph in one line, running on as in any book. A sentence not put the English way yet stands there in its
 * Russian, quietly, so the story does not jump; a sentence whose other lines are open is shaded.
 */
internal fun buildFlow(
    paragraph: Paragraph,
    line: Line,
    colors: PaperColors,
    style: ReaderStyle,
    nouns: Boolean,
    open: Set<Int>,
): Built {
    val hits = mutableListOf<NounHit>()
    val ranges = mutableListOf<IntRange>()
    val text = buildAnnotatedString {
        paragraph.sentences.forEachIndexed { index, sentence ->
            if (index > 0) append(" ")
            val start = length
            if (sentence.text(line).isBlank()) {
                withStyle(style.untranslated) { append(sentence.ours) }
            } else {
                addLine(sentence, index, line, colors, nouns, hits)
            }
            ranges += start until length
            if (index in open) addStyle(SpanStyle(background = colors.open), start, length)
        }
    }
    return Built(text, hits, ranges)
}

/**
 * The noun under a tap. A tap is taken to the nearest letter, so one past the end of a line would land on its last
 * word: it counts only if it is inside that letter's box, give or take [slack] pixels.
 */
internal fun Built.nounAt(layout: TextLayoutResult, position: Offset, slack: Float): NounHit? {
    val offset = layout.getOffsetForPosition(position)
    return listOf(offset, offset - 1).firstNotNullOfOrNull { at ->
        if (at < 0 || at >= layout.layoutInput.text.length) return@firstNotNullOfOrNull null
        val box = layout.getBoundingBox(at)
        val inside = position.x >= box.left - slack && position.x <= box.right + slack &&
            position.y >= box.top - slack && position.y <= box.bottom + slack
        if (!inside) null else hits.find { at >= it.start && at < it.end }
    }
}

/**
 * Text that knows where it was tapped: on a noun, or elsewhere and at which letter. Its nouns are also there for a
 * screen reader, as actions.
 */
@Composable
internal fun TappableText(
    built: Built,
    style: TextStyle,
    modifier: Modifier = Modifier,
    describe: (NounHit) -> String,
    onNoun: (NounHit) -> Unit,
    onTap: (offset: Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val noun by rememberUpdatedState(onNoun)
    val tap by rememberUpdatedState(onTap)
    BasicText(
        text = built.text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .pointerInput(built) {
                val slack = 4f * density
                detectTapGestures { position ->
                    val laid = layout ?: return@detectTapGestures
                    val hit = built.nounAt(laid, position, slack)
                    if (hit != null) noun(hit) else tap(laid.getOffsetForPosition(position))
                }
            }
            .semantics {
                if (built.hits.isNotEmpty()) {
                    customActions = built.hits.map { hit -> CustomAccessibilityAction(describe(hit)) { noun(hit); true } }
                }
            },
    )
}

/**
 * What scrolls follows a finger and the wheel and ignores a mouse's drag. On a desktop a page is also dragged with the
 * mouse, as in `apps/learn`. A finger is left to it.
 */
internal fun Modifier.mouseDragScrolls(list: ScrollableState): Modifier = pointerInput(list) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Mouse) return@awaitEachGesture
        val moved = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            list.dispatchRawDelta(-over)
            change.consume()
        } ?: return@awaitEachGesture
        verticalDrag(moved.id) { change ->
            list.dispatchRawDelta(-change.positionChange().y)
            change.consume()
        }
    }
}

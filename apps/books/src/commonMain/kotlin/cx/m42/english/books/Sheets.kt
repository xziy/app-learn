package cx.m42.english.books

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.ToggleRow

/**
 * A sheet over the page, from below: the page stays where it was under it. A tap beside it or back closes it. Not a
 * window of the host's, which Superizer does not allow an app.
 */
@Composable
private fun SheetFrame(tag: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val tokens = AppTheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight * 0.85f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .testTag("$tag-scrim"),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = PageWidth)
                .fillMaxWidth()
                .heightIn(max = tall)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(tokens.background)
                // Taken here, so a tap on the sheet between its widgets does not fall through to the scrim.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .testTag(tag),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Chip(title: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = AppTheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.foreground else tokens.surface)
            .border(1.dp, if (selected) tokens.foreground else tokens.border, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = AppTheme.body, color = if (selected) tokens.background else tokens.foreground, maxLines = 1)
    }
}

/** The lines, the page, the type and the nouns: how the person likes to read, the same for every book. */
@Composable
internal fun SettingsSheet(
    book: Book,
    settings: ReaderSettings,
    strings: BooksStrings,
    onSettings: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
) {
    SheetFrame("books:settings", onClose) {
        Text(text = strings.settings, style = AppTheme.header, color = AppTheme.foreground)

        Caption(strings.linesTitle)
        Column {
            Line.entries.forEach { line ->
                val can = line != Line.Transcription || book.transcription
                ToggleRow(
                    label = strings.line(line),
                    checked = can && line in settings.lines,
                    onCheckedChange = { onSettings(settings.toggle(line)) },
                    enabled = can,
                    modifier = Modifier.testTag("books:line-${line.key}"),
                )
            }
        }
        if (!book.transcription) Text(text = strings.noTranscription, style = AppTheme.footnote, color = AppTheme.muted)
        Text(text = strings.linesHint, style = AppTheme.footnote, color = AppTheme.muted)

        Caption(strings.paperTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper.entries.forEach { paper ->
                Chip(strings.paper(paper), settings.paper == paper, "books:paper-${paper.key}", Modifier.weight(1f)) {
                    onSettings(settings.copy(paper = paper))
                }
            }
        }

        Caption(strings.fontTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("A−", false, "books:font-down", Modifier.weight(1f)) {
                onSettings(settings.copy(font = (settings.font - FontStep).coerceAtLeast(MinFont)))
            }
            Text(
                text = settings.font.toString(),
                style = AppTheme.subtitle,
                color = AppTheme.foreground,
                modifier = Modifier.padding(horizontal = 8.dp).testTag("books:font-size"),
            )
            Chip("A+", false, "books:font-up", Modifier.weight(1f)) {
                onSettings(settings.copy(font = (settings.font + FontStep).coerceAtMost(MaxFont)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(strings.serif, settings.serif, "books:serif", Modifier.weight(1f)) { onSettings(settings.copy(serif = true)) }
            Chip(strings.sans, !settings.serif, "books:sans", Modifier.weight(1f)) { onSettings(settings.copy(serif = false)) }
        }

        ToggleRow(
            label = strings.nouns,
            checked = settings.nouns,
            onCheckedChange = { onSettings(settings.copy(nouns = it)) },
            modifier = Modifier.testTag("books:nouns"),
        )
        Text(text = strings.nounsHint, style = AppTheme.footnote, color = AppTheme.muted)

        AppButton(
            text = strings.done,
            onClick = onClose,
            variant = ButtonVariant.Outline,
            size = ButtonSize.Default,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("books:settings-done"),
        )
    }
}

/** The chapters of the book: a tap goes to one. */
@Composable
internal fun ContentsSheet(book: Book, strings: BooksStrings, colors: PaperColors, onClose: () -> Unit, onGo: (Int) -> Unit) {
    SheetFrame("books:contents", onClose) {
        Text(text = strings.contents, style = AppTheme.header, color = AppTheme.foreground)
        book.headings.forEach { index ->
            val title = book.paragraphs[index].sentences.joinToString(" ") { it.ours }
            Text(
                text = title,
                style = AppTheme.body.copy(fontFamily = FontFamily.Serif),
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onGo(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag("books:chapter-$index"),
            )
        }
    }
}

/**
 * A noun of a sentence: the word, how it is said, what it means here and how it stands in each line, then what the
 * dictionary of the lexicon skill knows of it, and how often the book has it.
 */
@Composable
internal fun WordSheet(book: Book, sentence: Sentence, index: Int, strings: BooksStrings, colors: PaperColors, onClose: () -> Unit) {
    val noun = sentence.nouns.getOrNull(index) ?: return
    val word = book.words[noun.word]
    val sound = word?.transcription ?: soundOf(sentence, index)
    val colour = colors.noun(index)
    SheetFrame("books:word", onClose) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = noun.word,
                style = AppTheme.title.copy(fontFamily = FontFamily.Serif),
                color = colour,
                modifier = Modifier.testTag("books:word-word"),
            )
            if (sound != null) {
                Text(
                    text = sound.replace("**", ""),
                    style = AppTheme.subtitle,
                    color = AppTheme.muted,
                    modifier = Modifier.padding(bottom = 4.dp).testTag("books:word-sound"),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Caption(strings.here)
            Text(
                text = noun.meaning,
                style = AppTheme.header,
                color = AppTheme.foreground,
                modifier = Modifier.testTag("books:word-meaning"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Line.English, Line.Thought, Line.Ours).forEach { line ->
                val spelled = noun.spelling(line) ?: return@forEach
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = strings.inLine(line), style = AppTheme.body, color = AppTheme.muted)
                    Text(
                        text = spelled,
                        style = AppTheme.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colour,
                        modifier = Modifier.testTag("books:word-${line.key}"),
                    )
                }
            }
        }
        if (word == null) {
            Text(text = strings.notInDictionary, style = AppTheme.footnote, color = AppTheme.muted, modifier = Modifier.testTag("books:word-none"))
        } else {
            val others = word.senses.filter { it.id != noun.sense && it.ru.isNotEmpty() }
            if (others.isNotEmpty()) {
                Caption(strings.otherSenses)
                others.forEach { sense ->
                    Text(
                        text = sense.ru.joinToString(", ") + (sense.en?.let { " — $it" } ?: ""),
                        style = AppTheme.body,
                        color = AppTheme.foreground,
                        modifier = Modifier.testTag("books:word-sense-${sense.id ?: "main"}"),
                    )
                }
            }
            val about = listOfNotNull(word.countable?.let(strings.countable), word.number?.let(strings.number))
            if (word.plural.isNotEmpty() || about.isNotEmpty()) {
                Caption(strings.plural)
                word.plural.forEach { form ->
                    Text(
                        text = listOf(form.word, form.transcription.replace("**", "")).filter { it.isNotBlank() }.joinToString(" "),
                        style = AppTheme.body,
                        color = AppTheme.foreground,
                    )
                }
                about.forEach { Text(text = it, style = AppTheme.footnote, color = AppTheme.muted) }
            }
            if (word.notes.isNotBlank()) {
                Caption(strings.notes)
                Text(text = word.notes, style = AppTheme.body, color = AppTheme.foreground)
            }
        }
        Text(
            text = strings.inBook(book.counts[noun.key] ?: 1),
            style = AppTheme.footnote,
            color = AppTheme.muted,
            modifier = Modifier.testTag("books:word-count"),
        )
        AppButton(
            text = strings.done,
            onClick = onClose,
            variant = ButtonVariant.Outline,
            size = ButtonSize.Default,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("books:word-done"),
        )
    }
}

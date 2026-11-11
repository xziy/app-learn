package cx.m42.english.books

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.BackIcon
import cx.m42.superizer.ui.components.IconButton
import cx.m42.superizer.ui.platform.SystemBackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Three lines, for the contents. SVG over 24×24. */
private const val LIST = "M4 6H20V8H4V6ZM4 11H20V13H4V11ZM4 16H14V18H4V16Z"

/** How long the page has to stand still before its place is written. */
private const val PlaceDelayMillis = 400L

/** A scroll by more than this many pixels at once hides the bars going down and brings them back going up. */
private const val BarsSlop = 6f

private val BarHeight = 56.dp

/** What lies over the page. */
internal sealed interface Sheet {
    data object Settings : Sheet
    data object Contents : Sheet
    data class Word(val sentence: Sentence, val noun: Int) : Sheet
}

/** A sentence of the book: its paragraph and its place there. */
internal data class SentenceId(val paragraph: Int, val sentence: Int)

/**
 * A book, full screen, as one long page. Each sentence has the lines the person chose, the nouns in each a colour of
 * their own; with one line the text runs on as in any book. A tap on a noun shows what it is, a tap elsewhere on a
 * sentence opens its other lines under it. The bars go away while reading down and come back going up.
 */
@Composable
internal fun Reader(
    book: Book,
    start: Place?,
    settings: ReaderSettings,
    colors: PaperColors,
    strings: BooksStrings,
    onPlace: (Place) -> Unit,
    onSettings: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    val lastItem = book.paragraphs.size + 1
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = (start?.item ?: 0).coerceIn(0, lastItem),
        initialFirstVisibleItemScrollOffset = start?.offset ?: 0,
    )
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var bars by remember { mutableStateOf(true) }
    val open = remember { mutableStateListOf<SentenceId>() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val style = remember(settings, colors) { ReaderStyle(settings, colors) }
    val shown = settings.shown(book)
    val place by rememberUpdatedState(onPlace)
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }
    val atEnd by remember { derivedStateOf { !list.canScrollForward } }
    val percent by remember {
        derivedStateOf { if (atEnd) 100 else percentRead(Place(list.firstVisibleItemIndex), book.paragraphs.size) ?: 0 }
    }

    // Written once the page stands still, and once more on the way out.
    LaunchedEffect(list) {
        snapshotFlow { Place(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }.collectLatest {
            delay(PlaceDelayMillis)
            place(it)
        }
    }
    DisposableEffect(list) {
        onDispose { place(Place(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)) }
    }
    LaunchedEffect(sheet) {
        if (sheet == null) runCatching { focus.requestFocus() }
    }
    val barsOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -BarsSlop) bars = false else if (available.y > BarsSlop) bars = true
                return Offset.Zero
            }
        }
    }
    SystemBackHandler(enabled = true) { if (sheet != null) sheet = null else onBack() }

    fun font(step: Int) = onSettings(settings.copy(font = (settings.font + step).coerceIn(MinFont, MaxFont)))

    fun toggle(id: SentenceId) {
        if (id in open) open.remove(id) else open.add(id)
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (sheet != null) {
            if (event.key != Key.Escape) return false
            sheet = null
            return true
        }
        val line = with(density) { 64.dp.toPx() }
        val page = list.layoutInfo.viewportSize.height * 0.85f
        when (event.key) {
            Key.DirectionDown -> scope.launch { list.animateScrollBy(line) }
            Key.DirectionUp -> scope.launch { list.animateScrollBy(-line) }
            Key.PageDown -> scope.launch { list.animateScrollBy(page) }
            Key.PageUp -> scope.launch { list.animateScrollBy(-page) }
            Key.Spacebar -> scope.launch { list.animateScrollBy(if (event.isShiftPressed) -page else page) }
            Key.MoveHome -> scope.launch { list.scrollToItem(0) }
            Key.MoveEnd -> scope.launch { list.scrollToItem(lastItem) }
            Key.Equals, Key.Plus, Key.NumPadAdd -> font(FontStep)
            Key.Minus, Key.NumPadSubtract -> font(-FontStep)
            Key.Escape -> onBack()
            else -> return false
        }
        return true
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent(::onKey),
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(barsOnScroll)
                .mouseDragScrolls(list)
                .testTag("books:reader"),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + BarHeight + 12.dp,
                bottom = insets.calculateBottomPadding() + 56.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "head") { Head(book, shown, strings, colors, style) }
            itemsIndexed(book.paragraphs, key = { index, _ -> index }) { index, paragraph ->
                ParagraphView(
                    index = index,
                    paragraph = paragraph,
                    shown = shown,
                    book = book,
                    settings = settings,
                    colors = colors,
                    style = style,
                    strings = strings,
                    open = open,
                    onToggle = ::toggle,
                    onNoun = { sentence, noun -> sheet = Sheet.Word(sentence, noun) },
                )
            }
            item(key = "end") {
                Text(
                    text = strings.end,
                    style = style.of(Line.Ours).copy(textAlign = TextAlign.Center),
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("books:end"),
                )
            }
        }

        if (bars || atTop || atEnd || sheet != null) {
            TopBar(book, strings, colors, onBack, onContents = { sheet = Sheet.Contents }, onSettings = { sheet = Sheet.Settings })
            BottomBar(percent, colors, Modifier.align(Alignment.BottomCenter))
        }

        when (val current = sheet) {
            null -> Unit
            Sheet.Settings -> SettingsSheet(book, settings, strings, onSettings, onClose = { sheet = null })
            Sheet.Contents -> ContentsSheet(book, strings, colors, onClose = { sheet = null }) { paragraph ->
                sheet = null
                scope.launch { list.scrollToItem(book.itemOf(paragraph)) }
            }
            is Sheet.Word -> WordSheet(book, current.sentence, current.noun, strings, colors, onClose = { sheet = null })
        }
    }
}

@Composable
private fun TopBar(
    book: Book,
    strings: BooksStrings,
    colors: PaperColors,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.97f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("books:bar"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, label = strings.back, modifier = Modifier.testTag("books:back")) { tint -> BackIcon(tint) }
            Text(
                text = book.title,
                style = AppTheme.subtitle.copy(fontFamily = FontFamily.Serif),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (book.headings.isNotEmpty()) {
                IconButton(onClick = onContents, label = strings.contents, modifier = Modifier.testTag("books:contents-open")) { tint ->
                    AppIconGlyph(AppIcon.Path(LIST), tint)
                }
            }
            IconButton(onClick = onSettings, label = strings.settings, modifier = Modifier.testTag("books:settings-open")) { tint ->
                Text(text = "Aa", style = AppTheme.subtitle.copy(fontFamily = FontFamily.Serif), color = tint)
            }
        }
    }
}

@Composable
private fun BottomBar(percent: Int, colors: PaperColors, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.97f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressLine(percent, colors, Modifier.weight(1f))
            Text(
                text = "$percent%",
                style = AppTheme.footnote,
                color = colors.muted,
                modifier = Modifier.width(40.dp).testTag("books:percent"),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** What the book is, and which line is which. */
@Composable
private fun Head(book: Book, shown: List<Line>, strings: BooksStrings, colors: PaperColors, style: ReaderStyle) {
    Column(
        modifier = Modifier.widthIn(max = PageWidth).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = book.title,
            style = style.of(Line.Ours, heading = true).copy(fontSize = AppTheme.title.fontSize, lineHeight = AppTheme.title.lineHeight),
            color = colors.text,
            modifier = Modifier.testTag("books:title"),
        )
        if (book.author.isNotBlank()) Text(text = book.author, style = AppTheme.body, color = colors.muted)
        Text(text = strings.sentences(book.sentences, book.translated), style = AppTheme.footnote, color = colors.muted)
        if (shown.size > 1) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { line ->
                    val look = style.of(line)
                    Text(
                        text = strings.line(line),
                        style = look.copy(fontSize = AppTheme.footnote.fontSize, lineHeight = AppTheme.footnote.lineHeight),
                        // Unstyled's Text takes its colour from here, not from the style.
                        color = look.color,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParagraphView(
    index: Int,
    paragraph: Paragraph,
    shown: List<Line>,
    book: Book,
    settings: ReaderSettings,
    colors: PaperColors,
    style: ReaderStyle,
    strings: BooksStrings,
    open: List<SentenceId>,
    onToggle: (SentenceId) -> Unit,
    onNoun: (Sentence, Int) -> Unit,
) {
    val describe = { sentence: Sentence -> { hit: NounHit -> sentence.nouns[hit.noun].let { "${it.form} — ${it.meaning}" } } }
    val opened = open.filter { it.paragraph == index }.map { it.sentence }.toSet()
    Column(
        modifier = Modifier
            .widthIn(max = PageWidth)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = if (paragraph.heading) 28.dp else 0.dp, bottom = if (paragraph.heading) 18.dp else 22.dp)
            .testTag("books:p-$index"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (shown.size == 1) {
            val line = shown.single()
            val built = remember(paragraph, line, colors, style, settings.nouns, opened) {
                buildFlow(paragraph, line, colors, style, settings.nouns, opened)
            }
            TappableText(
                built = built,
                style = style.of(line, paragraph.heading),
                modifier = Modifier.fillMaxWidth().testTag("books:flow-$index"),
                describe = { hit -> describe(paragraph.sentences[hit.sentence])(hit) },
                onNoun = { hit -> onNoun(paragraph.sentences[hit.sentence], hit.noun) },
                onTap = { offset -> built.sentenceAt(offset)?.let { onToggle(SentenceId(index, it)) } },
            )
            opened.sorted().forEach { at ->
                val sentence = paragraph.sentences[at]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(14.dp)
                        .testTag("books:open-$index-$at"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    linesOf(sentence, Line.entries, book).forEach { other ->
                        SentenceLine(sentence, other, colors, style, settings.nouns, describe(sentence),
                            tag = "books:s-$index-$at-${other.key}",
                            onNoun = { onNoun(sentence, it) },
                            onTap = { onToggle(SentenceId(index, at)) },
                        )
                    }
                }
            }
        } else {
            paragraph.sentences.forEachIndexed { at, sentence ->
                val lines = linesOf(sentence, shown, book)
                val hidden = if (at in opened) linesOf(sentence, Line.entries - shown.toSet(), book) else emptyList()
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("books:s-$index-$at"),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    lines.ifEmpty { listOf(Line.Ours) }.forEach { line ->
                        SentenceLine(sentence, line, colors, style, settings.nouns, describe(sentence),
                            tag = "books:s-$index-$at-${line.key}",
                            heading = paragraph.heading,
                            onNoun = { onNoun(sentence, it) },
                            onTap = { onToggle(SentenceId(index, at)) },
                        )
                    }
                    if (!sentence.translated) {
                        Text(text = strings.untranslated, style = AppTheme.footnote, color = colors.muted)
                    }
                    if (hidden.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("books:open-$index-$at"),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            hidden.forEach { line ->
                                SentenceLine(sentence, line, colors, style, settings.nouns, describe(sentence),
                                    tag = "books:s-$index-$at-${line.key}",
                                    onNoun = { onNoun(sentence, it) },
                                    onTap = { onToggle(SentenceId(index, at)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Of [lines], those this sentence has something in. */
private fun linesOf(sentence: Sentence, lines: List<Line>, book: Book): List<Line> =
    lines.filter { (it != Line.Transcription || book.transcription) && sentence.text(it).isNotBlank() }

@Composable
private fun SentenceLine(
    sentence: Sentence,
    line: Line,
    colors: PaperColors,
    style: ReaderStyle,
    nouns: Boolean,
    describe: (NounHit) -> String,
    tag: String,
    heading: Boolean = false,
    onNoun: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val built = remember(sentence, line, colors, nouns) { buildLine(sentence, line, colors, nouns) }
    TappableText(
        built = built,
        style = style.of(line, heading),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        describe = describe,
        onNoun = { onNoun(it.noun) },
        onTap = { onTap() },
    )
}

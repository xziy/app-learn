package cx.m42.english.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.theme.LocalTokens
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.BackIcon
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.IconButton
import cx.m42.superizer.ui.components.PlusIcon

/** What the arrow at the top takes up: the shelf starts below it. */
internal val ArrowRoom = 64.dp

/** Wider than this a line of text is hard to follow, so on a desktop the page stays this wide in the middle. */
internal val PageWidth = 680.dp

/**
 * The shelf, and the book open on it full screen. Everything is on the person's page — light, warm or dark — with the
 * host's tokens made over from it, so the widgets on it follow.
 */
@Composable
internal fun BooksScreen(instance: BooksInstance, strings: BooksStrings) {
    val settings by instance.settings.collectAsState()
    val shelf by instance.shelf.collectAsState()
    val opened by instance.opened.collectAsState()
    val places by instance.places.collectAsState()
    val fetch by instance.fetch.collectAsState()
    val colors = remember(settings.paper) { settings.paper.colors() }
    val host = AppTheme
    val tokens = remember(host, colors) { host.on(colors) }
    // Here rather than in the shelf, which is gone while a book is open: coming back finds it where it was.
    val shelfScroll = rememberLazyListState()

    CompositionLocalProvider(LocalTokens provides tokens) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            when (val current = opened) {
                null -> fetch?.let { FetchScreen(instance, it, strings, colors) } ?: ShelfScreen(
                    shelf = shelf,
                    places = places,
                    strings = strings,
                    colors = colors,
                    onOpen = instance::open,
                    onClose = instance::close,
                    list = shelfScroll,
                    onAdd = if (instance.canFetch) instance::openFetch else null,
                    onRemove = instance::remove,
                )
                is Opened.Loading -> TopArrow(strings.back, "books:back", instance::closeBook)
                is Opened.Failed -> {
                    Text(
                        text = strings.cannotOpen,
                        style = AppTheme.body,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag("books:cannot-open"),
                    )
                    TopArrow(strings.back, "books:back", instance::closeBook)
                }
                is Opened.Ready -> key(current.book.slug) {
                    Reader(
                        book = current.book,
                        start = current.start,
                        settings = settings,
                        colors = colors,
                        strings = strings,
                        onPlace = { instance.place(current.book.slug, it) },
                        onSettings = instance::update,
                        onBack = instance::closeBook,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfScreen(
    shelf: Shelf,
    places: Map<String, Place>,
    strings: BooksStrings,
    colors: PaperColors,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    list: LazyListState,
    /** Null where the host cannot download or keep books for this app: no button to promise it. */
    onAdd: (() -> Unit)?,
    onRemove: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (shelf) {
            Shelf.Loading -> Unit
            is Shelf.Ready -> if (shelf.books.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = strings.empty,
                        style = AppTheme.body,
                        color = colors.muted,
                        modifier = Modifier.testTag("books:empty"),
                    )
                    if (onAdd != null) {
                        AppButton(
                            text = strings.addBook,
                            onClick = onAdd,
                            size = ButtonSize.Default,
                            modifier = Modifier.testTag("books:add"),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = list,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = ArrowRoom)
                        .mouseDragScrolls(list)
                        .testTag("books:shelf"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Row(
                            modifier = Modifier.widthIn(max = PageWidth).fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.shelfTitle,
                                style = AppTheme.title,
                                color = colors.text,
                                modifier = Modifier.weight(1f),
                            )
                            if (onAdd != null) {
                                IconButton(onClick = onAdd, label = strings.addBook, modifier = Modifier.testTag("books:add")) { tint ->
                                    PlusIcon(tint)
                                }
                            }
                        }
                    }
                    items(shelf.books, key = { it.slug }) { book ->
                        BookRow(book, places[book.slug], strings, colors, onOpen = { onOpen(book.slug) }, onRemove = { onRemove(book.slug) })
                    }
                }
            }
        }
        TopArrow(strings.close, "books:close", onClose)
    }
}

@Composable
private fun BookRow(
    book: BookInfo,
    place: Place?,
    strings: BooksStrings,
    colors: PaperColors,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val percent = percentRead(place, book.paragraphs)
    var confirming by remember(book.slug, book.file) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .widthIn(max = PageWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(16.dp)
            .testTag("books:book-${book.slug}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = book.title,
            style = AppTheme.header.copy(fontFamily = FontFamily.Serif),
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.author.isNotBlank()) {
            Text(text = book.author, style = AppTheme.body, color = colors.muted)
        }
        Text(text = strings.sentences(book.sentences, book.translated), style = AppTheme.footnote, color = colors.muted)
        Spacer(Modifier.height(6.dp))
        ProgressLine(percent ?: 0, colors)
        Text(
            text = percent?.let(strings.read) ?: strings.notStarted,
            style = AppTheme.footnote,
            color = colors.muted,
            modifier = Modifier.testTag("books:read-${book.slug}"),
        )
        // A downloaded book can go again, from the shelf as from the folder; one the build carries cannot.
        if (book.file != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = strings.downloaded, style = AppTheme.footnote, color = colors.muted, modifier = Modifier.weight(1f))
                if (confirming) {
                    AppButton(
                        text = strings.removeConfirm,
                        onClick = onRemove,
                        variant = ButtonVariant.Destructive,
                        size = ButtonSize.Sm,
                        modifier = Modifier.testTag("books:remove-confirm-${book.slug}"),
                    )
                    AppButton(
                        text = strings.keep,
                        onClick = { confirming = false },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Sm,
                        modifier = Modifier.testTag("books:remove-keep-${book.slug}"),
                    )
                } else {
                    AppButton(
                        text = strings.remove,
                        onClick = { confirming = true },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Sm,
                        modifier = Modifier.testTag("books:remove-${book.slug}"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgressLine(percent: Int, colors: PaperColors, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.border)) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight().background(colors.muted))
    }
}

@Composable
internal fun TopArrow(label: String, tag: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        label = label,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 4.dp, top = 12.dp)
            .testTag(tag),
    ) { tint -> BackIcon(tint) }
}

package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.theme.LocalTokens
import cx.m42.superizer.theme.Tokens
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.BackIcon
import cx.m42.superizer.ui.components.IconButton
import cx.m42.superizer.ui.platform.SystemBackHandler
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Inside the language: the list of chosen verbs, the list of seen ones, and the feed, full screen, one card at a time,
 * as in Reels. Up and down is the next card, left and right goes through a phrase's steps, and a tap goes
 * forward: along the phrase, then to the next card. Each verb opens with a card about it, each of its tenses
 * with a card about the tense, and everything of one verb is in that verb's colour. The last step of a phrase
 * is said aloud as it comes up, and play goes through all of it by itself.
 *
 * The host's tokens turned inside out rather than colours of its own, so the widgets inside follow by
 * themselves.
 */
@Composable
internal fun LearnScreen(instance: LearnInstance, strings: LearnStrings) {
    val light = AppTheme
    val reel = remember(light) { light.inverted() }
    val state by instance.state.collectAsState()
    // Where the feed is open from; null while a list is on screen.
    var opened by remember { mutableStateOf<Start?>(null) }
    // The seen verb being watched again: the feed is then of it alone, and leaves no mark.
    var again by remember { mutableStateOf<Verb?>(null) }
    var seenList by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf<Verb?>(null) }
    // The phrase whose form the cap's page is showing.
    var rule by remember { mutableStateOf<FeedPage.Phrase?>(null) }
    var dictionary by remember { mutableStateOf(false) }
    // The word whose page is open, over the feed or the dictionary.
    var word by remember { mutableStateOf<WordOpen?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WordFilter.Seen) }
    val lexicon by instance.lexicon.collectAsState()
    val marks by instance.marks.collectAsState()
    val scope = rememberCoroutineScope()
    // Here rather than in the lists, which are gone while the feed is open: coming back finds them where they were.
    val listScroll = rememberLazyListState()
    val seenScroll = rememberLazyListState()
    val dictionaryScroll = rememberLazyListState()

    fun leaveFeed() {
        if (again == null) instance.refresh()
        again = null
        opened = null
    }

    /** A phrase of a word's page: the feed of its verb alone, on that phrase's English. */
    fun openPhrase(verb: Verb, sentence: Sentence) {
        val page = buildPages(listOf(verb)).indexOfFirst { it is FeedPage.Phrase && it.sentence.address == sentence.address }
        if (opened != null && again == null) instance.refresh()
        word = null
        again = verb
        opened = Start(page.coerceAtLeast(0), if (page >= 0) Step.English.ordinal else 0)
    }

    SystemBackHandler(enabled = word != null || about != null || rule != null || opened != null || seenList || dictionary) {
        when {
            word != null -> word = null
            about != null -> about = null
            rule != null -> rule = null
            opened != null -> leaveFeed()
            seenList -> seenList = false
            else -> dictionary = false
        }
    }
    CompositionLocalProvider(LocalTokens provides reel) {
        Box(Modifier.fillMaxSize().background(reel.background)) {
            when (val current = state) {
                LearnState.Loading -> Unit
                LearnState.Empty -> Centered { Line(strings.empty, reel.body, "learn:empty") }
                is LearnState.Ready -> {
                    val session = current.session
                    val start = opened
                    val watching = again
                    when {
                        start == null && dictionary -> Dictionary(
                            lexicon = lexicon,
                            marks = marks,
                            strings = strings,
                            query = query,
                            onQuery = { query = it },
                            filter = filter,
                            onFilter = { filter = it },
                            onOpen = { word = WordOpen(it) },
                            onKnow = instance::know,
                            onForgetSeen = instance::forgetSeen,
                            list = dictionaryScroll,
                        )
                        start == null && seenList -> SeenList(
                            session = session,
                            strings = strings,
                            read = { word -> instance.read(session, word) },
                            onOpen = { verb ->
                                again = verb
                                opened = Start(0)
                            },
                            onUnsee = instance::unsee,
                            onUnseeAll = instance::unseeAll,
                            list = seenScroll,
                        )
                        start == null -> {
                            val resume = instance.resume(session)
                            VerbList(
                                session = session,
                                strings = strings,
                                canResume = resume != null,
                                onTheme = instance::chooseTheme,
                                onGo = { opened = resume ?: Start(0) },
                                onPick = instance::pickAtRandom,
                                onOpen = { word -> opened = Start(session.firstPageOf(word)) },
                                onChoose = instance::choose,
                                onUnchoose = instance::unchoose,
                                onSeen = { seenList = true },
                                wordsSeen = marks.seen.size,
                                onDictionary = { dictionary = true },
                                list = listScroll,
                            )
                        }
                        watching != null -> key(watching.word, start) {
                            Feed(
                                instance = instance,
                                session = remember { session.only(watching) },
                                start = start,
                                strings = strings,
                                done = strings.againDone,
                                paused = about != null || rule != null || word != null,
                                onSettled = { _, _, _ -> },
                                onAbout = { about = it },
                                onRule = { rule = it },
                                lexicon = lexicon,
                                known = marks.known,
                                onWord = { word = WordOpen(it) },
                                onList = ::leaveFeed,
                            )
                        }
                        else -> key(start) {
                            Feed(
                                instance = instance,
                                session = session,
                                start = start,
                                strings = strings,
                                done = strings.done,
                                paused = about != null || rule != null || word != null,
                                onSettled = instance::settled,
                                onAbout = { about = it },
                                onRule = { rule = it },
                                lexicon = lexicon,
                                known = marks.known,
                                onWord = { word = WordOpen(it) },
                                onList = ::leaveFeed,
                            )
                        }
                    }
                }
            }
            // Over the feed rather than in its place, so the feed is where it was on the way back.
            val reading = about
            val ruling = rule
            val looking = word
            when {
                looking != null -> WordPage(
                    open = looking,
                    lexicon = lexicon,
                    marks = marks,
                    themes = (state as? LearnState.Ready)?.session?.themes.orEmpty(),
                    strings = strings,
                    canSpeak = instance.canSpeak,
                    onSay = { text -> scope.launch { instance.speak(text) } },
                    onKnow = instance::know,
                    read = instance::readIn,
                    onPhrase = ::openPhrase,
                    onBack = { word = null },
                )
                reading != null -> AboutVerb(reading, strings, onBack = { about = null })
                ruling != null -> RulePage(ruling, strings, onBack = { rule = null })
                opened != null -> TopArrow(strings.back, "learn:to-list", ::leaveFeed)
                seenList -> TopArrow(strings.back, "learn:seen-back") { seenList = false }
                dictionary -> TopArrow(strings.back, "learn:dict-back") { dictionary = false }
                else -> TopArrow(strings.close, "learn:close", instance::close)
            }
        }
    }
}

@Composable
internal fun TopArrow(label: String, tag: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        label = label,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 4.dp, top = 16.dp)
            .testTag(tag),
    ) { tint -> BackIcon(tint) }
}

private fun Tokens.inverted(): Tokens = copy(
    background = foreground,
    foreground = background,
    primary = background,
    primaryForeground = foreground,
    muted = disabled,
    disabled = muted,
)

@Composable
private fun Feed(
    instance: LearnInstance,
    session: Session,
    start: Start,
    strings: LearnStrings,
    done: String,
    paused: Boolean,
    onSettled: (from: Int?, page: Int, step: Int) -> Unit,
    onAbout: (Verb) -> Unit,
    onRule: (FeedPage.Phrase) -> Unit,
    lexicon: Lexicon?,
    known: Set<String>,
    onWord: (NounUse) -> Unit,
    onList: () -> Unit,
) {
    val pages = session.pages
    val scope = rememberCoroutineScope()
    val feed = rememberPagerState(initialPage = start.page) { pages.size + 1 }
    // Each phrase has its own steps; what the feed needs is those of the phrase on screen.
    val steps = remember { mutableStateMapOf<Int, PagerState>() }
    // How far each card on screen is scrolled, by page and step: what the mouse and the keys go through first.
    val cards = remember { mutableMapOf<Pair<Int, Int>, ScrollState>() }

    fun card(): ScrollState? {
        val page = feed.currentPage
        return cards[page to (steps[page]?.currentPage ?: 0)]
    }

    var playing by remember { mutableStateOf(false) }
    var sayAgain by remember { mutableIntStateOf(0) }
    // A press on a moving pager stops it and counts as a drag, so a quick second tap would be lost and the page
    // would snap back. While a tap's slide is under way the pagers take no drags, and the press stays a tap.
    var sliding by remember { mutableIntStateOf(0) }
    // Where the feed is at rest, as page and step. Null until the phrase on screen has its steps.
    val place by remember {
        derivedStateOf {
            val page = feed.settledPage
            val own = steps[page]
            when {
                pages.getOrNull(page) !is FeedPage.Phrase -> page to 0
                own == null -> null
                else -> page to own.settledPage
            }
        }
    }
    // What was last spoken for: coming back to a step, or stopping on it, is no reason to speak.
    var spokenFor by remember { mutableStateOf<Pair<Pair<Int, Int>, Int>?>(null) }

    fun go(move: Move) {
        scope.launch {
            when (move) {
                Move.NextCard -> if (!readOn(card(), forward = true)) feed.animateScrollToPage(feed.currentPage + 1)
                Move.PreviousCard -> if (!readOn(card(), forward = false)) feed.animateScrollToPage(feed.currentPage - 1)
                Move.NextStep -> steps[feed.currentPage]?.let { it.animateScrollToPage(it.currentPage + 1) }
                Move.PreviousStep -> steps[feed.currentPage]?.let { it.animateScrollToPage(it.currentPage - 1) }
                Move.Forward -> {
                    sliding++
                    try {
                        // Where the pagers are heading, not where they are: a quick second tap goes on from there.
                        val page = feed.targetPage
                        val own = steps[page]
                        if (own != null && own.targetPage < own.pageCount - 1) {
                            own.animateScrollToPage(own.targetPage + 1)
                        } else {
                            feed.animateScrollToPage(page + 1)
                        }
                    } finally {
                        sliding--
                    }
                }
            }
        }
    }

    // A phrase the feed has gone away from starts from its first step when it comes back, the stored one included:
    // the stored step is only for the first time the feed opens on it. Those still composed off screen are put back here,
    // and the others have nothing kept, see `phraseSteps`.
    var leftStart by remember { mutableStateOf(false) }
    LaunchedEffect(feed) {
        snapshotFlow { feed.settledPage }.collect { here ->
            if (here != start.page) leftStart = true
            steps.filterKeys { it != here }.values.forEach { it.requestScrollToPage(0) }
        }
    }

    // The English of a phrase on screen is its nouns seen.
    LaunchedEffect(place) {
        val (page, step) = place ?: return@LaunchedEffect
        val entry = pages.getOrNull(page) as? FeedPage.Phrase ?: return@LaunchedEffect
        if (Step.entries[step] == Step.English) instance.saw(entry.sentence.nouns)
    }

    // The page it last rested on, to tell a move forward from a move back.
    var rested by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(place) {
        place?.let { (page, step) ->
            onSettled(rested, page, step)
            rested = page
        }
    }

    // A new place, a press of play or of the speaker: whatever was going on stops, being cancelled, and this
    // starts. A step gets its seconds after its sound, so the last one has the time of the sound on top.
    LaunchedEffect(place, playing, paused, sayAgain) {
        val here = place ?: return@LaunchedEffect
        if (paused) return@LaunchedEffect
        val occasion = here to sayAgain
        val due = playing || (spokenFor != null && spokenFor != occasion)
        spokenFor = occasion
        val entry = pages.getOrNull(here.first)
        if (entry == null) {
            playing = false
            return@LaunchedEffect
        }
        if (due && entry is FeedPage.Phrase && Step.entries[here.second] == Step.English) {
            instance.speak(entry.sentence.english)
        }
        if (playing) {
            delay((if (entry is FeedPage.Phrase) StepSeconds else IntroSeconds) * 1_000L)
            go(Move.Forward)
        }
    }

    // By default a pager takes no fling from inside its pages, so a flick on anything that can scroll, even with nothing
    // to scroll, would slide the card back. What is to reach the feed is for `StopAtItsEnd` to say.
    val pageDefaults = PagerDefaults.pageNestedScrollConnection(feed, Orientation.Vertical)
    val flingsThrough = remember(pageDefaults) {
        object : NestedScrollConnection by pageDefaults {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity.Zero
        }
    }

    val focus = remember { FocusRequester() }
    // Again on the way back from the page over it: the arrow clicked there took the focus and is gone.
    LaunchedEffect(paused) { if (!paused) runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = feed,
            userScrollEnabled = sliding == 0,
            pageNestedScrollConnection = flingsThrough,
            modifier = Modifier
                .fillMaxSize()
                .testTag("learn:feed")
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val move = if (event.type == KeyEventType.KeyDown && !paused) event.key.move() else null
                    move?.let(::go)
                    move != null
                }
                .focusable()
                .mouseMoves(::card, ::go)
                .taps { go(Move.Forward) },
        ) { page ->
            when (val entry = pages.getOrNull(page)) {
                null -> Finish(done, strings, onList)
                is FeedPage.VerbCard -> VerbCardPage(entry.verb, strings, withHint = page == 0, cardScroll(cards, page))
                is FeedPage.TenseCard -> TenseCardPage(entry, cardScroll(cards, page))
                is FeedPage.Phrase -> {
                    val own = phraseSteps(if (page == start.page && !leftStart) start.step else 0)
                    DisposableEffect(page, own) {
                        steps[page] = own
                        onDispose { steps.remove(page) }
                    }
                    PhrasePage(
                        entry = entry,
                        position = page,
                        session = session,
                        pager = own,
                        cards = cards,
                        draggable = sliding == 0,
                        strings = strings,
                        canSpeak = instance.canSpeak,
                        onSay = { sayAgain++ },
                        onAbout = { onAbout(entry.verb) },
                        onRule = { onRule(entry) },
                        lexicon = lexicon,
                        known = known,
                        onWord = onWord,
                    )
                }
            }
        }
        if (feed.currentPage < pages.size) {
            IconButton(
                onClick = { playing = !playing },
                label = if (playing) strings.pause else strings.play,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 4.dp, top = 16.dp)
                    .testTag("learn:play"),
            ) { tint -> AppIconGlyph(AppIcon.Path(if (playing) PAUSE else PLAY), tint) }
        }
    }
}

/** SVG paths over a 24×24 viewBox. */
private const val PLAY = "M8 5V19L19 12Z"
private const val PAUSE = "M6 5H10V19H6ZM14 5H18V19H14Z"
internal const val SPEAKER =
    "M3 9V15H7L12 20V4L7 9H3Z" +
        "M16.5 12C16.5 10.23 15.48 8.71 14 7.97V16.03C15.48 15.29 16.5 13.77 16.5 12Z" +
        "M14 3.23V5.29C16.89 6.15 19 8.83 19 12C19 15.17 16.89 17.85 14 18.71V20.77C18.01 19.86 21 16.28 21 12C21 7.72 18.01 4.14 14 3.23Z"

private enum class Move { NextCard, PreviousCard, NextStep, PreviousStep, Forward }

private fun Key.move(): Move? = when (this) {
    Key.DirectionDown -> Move.NextCard
    Key.DirectionUp -> Move.PreviousCard
    Key.DirectionRight -> Move.NextStep
    Key.DirectionLeft -> Move.PreviousStep
    else -> null
}

/** How far a mouse has to be dragged to count as a swipe. */
private val MouseSwipe = 48.dp

/** One notch or one flick of a trackpad is one move, however many events it comes in. */
private const val WheelPauseMillis = 400L

/** How far one notch of the wheel scrolls a card. */
private val WheelNotch = 64.dp

/** How much of a card that does not fit one press of an arrow key shows anew. */
private const val KeyScrollShare = 0.75f

private fun ScrollState.hasMore(forward: Boolean): Boolean = if (forward) canScrollForward else canScrollBackward

/** A press of an arrow key through a card with more to read that way. False if it has none. */
private suspend fun readOn(card: ScrollState?, forward: Boolean): Boolean {
    if (card == null || !card.hasMore(forward)) return false
    card.animateScrollBy(card.viewportSize * KeyScrollShare * if (forward) 1 else -1)
    return true
}

/**
 * The steps of one phrase. `remember`, not `rememberPagerState`: the feed saves what its pages keep with `rememberSaveable`
 * and gives it back when a page comes back, and a phrase that comes back is to start from its first step.
 */
@Composable
private fun phraseSteps(initial: Int): PagerState = remember { PagerState(currentPage = initial) { Step.entries.size } }

/** The scroll of one card, told to the feed for as long as the card is there. */
@Composable
private fun cardScroll(cards: MutableMap<Pair<Int, Int>, ScrollState>, page: Int, step: Int = 0): ScrollState {
    val scroll = rememberScrollState()
    DisposableEffect(page, step, scroll) {
        cards[page to step] = scroll
        onDispose { cards.remove(page to step) }
    }
    return scroll
}

/**
 * A finger reading a long card stops at its end, and it takes a new swipe to go to the next one: otherwise a flick
 * through the text takes the card away. A swipe that did not move the card goes on to the feed as usual.
 */
private class StopAtItsEnd : NestedScrollConnection {
    private var moved = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) moved = true
        return if (moved) available else Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val kept = if (moved) available else Velocity.Zero
        moved = false
        return kept
    }
}

@Composable
private fun Modifier.scrollsAsACard(scroll: ScrollState): Modifier =
    nestedScroll(remember { StopAtItsEnd() }).verticalScroll(scroll)

/**
 * A pager follows a finger and ignores a mouse's drag, so on a desktop the same swipes are read here: a
 * drag moves once it is let go, and the wheel moves too, instead of scrolling the pager to a stop between
 * two cards. A finger is left to the pagers.
 *
 * A [card] with more to read that way scrolls first, and only a new drag or, after a pause, a new notch leaves it.
 */
private fun Modifier.mouseMoves(card: () -> ScrollState?, onMove: (Move) -> Unit): Modifier = pointerInput(Unit) {
    val threshold = MouseSwipe.toPx()
    val notch = WheelNotch.toPx()
    awaitPointerEventScope {
        var pressed: Offset? = null
        // This press has scrolled a card, so it is not a swipe as well.
        var scrolled = false
        var lastWheel = -WheelPauseMillis
        // The wheel is going through a card: however long it keeps turning, it takes a pause to leave the card.
        var reading = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (change.type != PointerType.Mouse) continue
            when (event.type) {
                PointerEventType.Press -> {
                    pressed = change.position
                    scrolled = false
                }
                PointerEventType.Move -> {
                    val drag = change.position - (pressed ?: continue)
                    val by = change.positionChange().y
                    val own = card()
                    if (own != null && abs(drag.y) > abs(drag.x) && abs(drag.y) > viewConfiguration.touchSlop && own.hasMore(forward = by < 0)) {
                        own.dispatchRawDelta(-by)
                        scrolled = true
                    }
                }
                PointerEventType.Release -> {
                    val drag = pressed?.let { change.position - it } ?: Offset.Zero
                    pressed = null
                    if (scrolled) change.consume()
                    val move = when {
                        scrolled -> null
                        abs(drag.x) < threshold && abs(drag.y) < threshold -> null
                        abs(drag.x) > abs(drag.y) -> if (drag.x < 0) Move.NextStep else Move.PreviousStep
                        else -> if (drag.y < 0) Move.NextCard else Move.PreviousCard
                    }
                    if (move != null) {
                        // It was a swipe, so it is not also a click on whatever it ended over.
                        change.consume()
                        onMove(move)
                    }
                }
                PointerEventType.Scroll -> {
                    change.consume()
                    val wheel = change.scrollDelta
                    val own = card()
                    if (own != null && abs(wheel.y) >= abs(wheel.x) && wheel.y != 0f && own.hasMore(forward = wheel.y > 0)) {
                        own.dispatchRawDelta(wheel.y * notch)
                        lastWheel = change.uptimeMillis
                        reading = true
                    } else if (change.uptimeMillis - lastWheel < WheelPauseMillis) {
                        if (reading) lastWheel = change.uptimeMillis
                    } else if (wheel != Offset.Zero) {
                        lastWheel = change.uptimeMillis
                        reading = false
                        onMove(
                            if (abs(wheel.x) > abs(wheel.y)) {
                                if (wheel.x > 0) Move.NextStep else Move.PreviousStep
                            } else {
                                if (wheel.y > 0) Move.NextCard else Move.PreviousCard
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A tap or a left click that no widget took: a button inside has consumed its press by the time it gets
 * here. A finger that moved belongs to the pagers, a mouse that moved to [mouseMoves].
 */
private fun Modifier.taps(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
        up.consume()
        onTap()
    }
}

@Composable
private fun PhrasePage(
    entry: FeedPage.Phrase,
    position: Int,
    session: Session,
    pager: PagerState,
    cards: MutableMap<Pair<Int, Int>, ScrollState>,
    draggable: Boolean,
    strings: LearnStrings,
    canSpeak: Boolean,
    onSay: () -> Unit,
    onAbout: () -> Unit,
    onRule: () -> Unit,
    lexicon: Lexicon?,
    known: Set<String>,
    onWord: (NounUse) -> Unit,
) {
    val sentence = entry.sentence
    val nouns = remember(sentence, lexicon) {
        nounMarks(sentence.english, sentence.nouns) { lexicon?.spellings(it) ?: setOf(it.lowercase()) }
    }
    Box(Modifier.fillMaxSize().background(verbColor(entry.verb.word)).testTag("learn:card-$position")) {
        HorizontalPager(state = pager, userScrollEnabled = draggable, modifier = Modifier.fillMaxSize()) { index ->
            val step = Step.entries[index]
            Centered(cardScroll(cards, position, index)) {
                Line(strings.step(step), AppTheme.label.copy(color = AppTheme.muted), "learn:label")
                Spacer(Modifier.height(16.dp))
                when (step) {
                    Step.Ours -> Line(sentence.ours, SentenceStyle, "learn:ours")
                    Step.Thought -> Line(sentence.thought, SentenceStyle, "learn:thought")
                    Step.English -> {
                        val linked = linkedPhrase(sentence.english, nouns, known, onWord)
                        BasicText(
                            text = linked,
                            style = SentenceStyle.copy(color = AppTheme.foreground, textAlign = TextAlign.Center),
                            modifier = Modifier.testTag("learn:english"),
                        )
                        Spacer(Modifier.height(20.dp))
                        BasicText(
                            text = linkedSound(sentence, nouns, known, AppTheme.foreground, onWord),
                            style = AppTheme.subtitle.copy(
                                fontWeight = AppTheme.body.fontWeight,
                                color = AppTheme.muted,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier.testTag("learn:transcription"),
                        )
                        Spacer(Modifier.height(20.dp))
                        if (canSpeak) {
                            IconButton(onClick = onSay, label = strings.say, modifier = Modifier.testTag("learn:say")) { tint ->
                                AppIconGlyph(AppIcon.Path(SPEAKER), tint, size = 28.dp)
                            }
                        } else {
                            Line(strings.noSpeech, AppTheme.body.copy(color = AppTheme.muted), "learn:no-speech")
                        }
                    }
                }
            }
        }
        Segments(pager, Modifier.align(Alignment.TopCenter))
        FormLine(
            entry,
            strings,
            onRule,
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // Between the arrow back and play, level with them.
                .padding(start = 50.dp, end = 50.dp, top = 23.dp),
        )
        Footer(entry.verb, session.inVerb(position), strings, onAbout, Modifier.align(Alignment.BottomStart))
    }
}

private val SentenceStyle = TextStyle(fontSize = 30.sp, lineHeight = 38.sp)

/** Where in the phrase this is, the way stories mark it: a bar per step. */
@Composable
private fun Segments(pager: PagerState, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("learn:segments"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(Step.entries.size) { index ->
            val reached = index <= pager.currentPage
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (reached) AppTheme.foreground else AppTheme.muted.copy(alpha = 0.4f)),
            )
        }
    }
}

/** The word this phrase is for, where Reels has the author and the caption. */
@Composable
private fun Footer(
    verb: Verb,
    inVerb: Pair<Int, Int>,
    strings: LearnStrings,
    onAbout: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = verb.word,
                style = AppTheme.subtitle,
                color = AppTheme.foreground,
                modifier = Modifier.testTag("learn:word"),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                verb.transcription?.let { sound ->
                    Text(
                        text = stressed(sound, AppTheme.foreground),
                        style = AppTheme.label,
                        color = AppTheme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("learn:word-sound"),
                    )
                }
            }
            Text(
                text = "${inVerb.first} / ${inVerb.second}",
                style = AppTheme.label,
                color = AppTheme.muted,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp).testTag("learn:counter"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = verb.meaning,
                style = AppTheme.footnote,
                color = AppTheme.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("learn:meaning"),
            )
            IconButton(
                onClick = onAbout,
                label = strings.about,
                modifier = Modifier.testTag("learn:about"),
            ) { tint -> LetterI(tint) }
        }
    }
}

/** The letter i in a ring: "there is more to read about this". */
@Composable
private fun LetterI(tint: Color) {
    Box(
        modifier = Modifier.size(26.dp).border(1.dp, tint.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "i", style = AppTheme.label, color = tint)
    }
}

/** The card a verb opens with: what it is, how it is said and what it means. */
@Composable
private fun VerbCardPage(verb: Verb, strings: LearnStrings, withHint: Boolean, scroll: ScrollState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(verbColor(verb.word))
            .testTag("learn:verb-card")
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Below the arrows, so what scrolls does not run under them.
            .padding(top = ArrowRoom)
            .scrollsAsACard(scroll)
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
    ) {
        VerbDetails(verb, strings)
        if (withHint) {
            Spacer(Modifier.height(32.dp))
            Line(strings.hint, AppTheme.footnote.copy(color = AppTheme.muted), "learn:hint")
        }
    }
}

/**
 * The cover of a tense, a panel rather than a phrase: which verb, which tense, which portion of cards comes next
 * and what the tense is for. All of it in Russian and in English at once, whatever language the app is in. It may
 * not fit a small phone, and then it scrolls.
 */
@Composable
private fun TenseCardPage(entry: FeedPage.TenseCard, scroll: ScrollState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(verbColor(entry.verb.word))
            .testTag("learn:tense-card")
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = ArrowRoom)
            .scrollsAsACard(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppTheme.foreground.copy(alpha = 0.08f))
                .border(1.dp, AppTheme.foreground.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                text = entry.verb.word,
                style = AppTheme.title,
                color = AppTheme.foreground,
                modifier = Modifier.testTag("learn:tense-word"),
            )
            entry.tense.form?.let { form ->
                Text(text = form, style = AppTheme.body, color = AppTheme.foreground, modifier = Modifier.testTag("learn:tense-form"))
            }
            CoverSide(entry, LearnStrings.Ru, "ru")
            CoverSide(entry, LearnStrings.En, "en")
        }
    }
}

/** What a cover says, in one language. The reason a tense does not exist is in the base in Russian only. */
@Composable
private fun CoverSide(entry: FeedPage.TenseCard, strings: LearnStrings, lang: String) {
    val tense = entry.tense
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.foreground.copy(alpha = 0.2f)))
    Spacer(Modifier.height(14.dp))
    strings.tenseVoice(tense)?.let { voice ->
        Text(text = voice, style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.testTag("learn:tense-voice-$lang"))
    }
    Text(
        text = strings.tenseTitle(tense),
        style = AppTheme.subtitle,
        color = AppTheme.foreground,
        modifier = Modifier.testTag("learn:tense-title-$lang"),
    )
    strings.tenseTime(tense)?.let { time ->
        Text(text = time, style = AppTheme.subtitle, color = AppTheme.foreground, modifier = Modifier.testTag("learn:tense-time-$lang"))
    }
    entry.portion?.let { portion ->
        Text(
            text = strings.portion(portion),
            style = AppTheme.label,
            color = AppTheme.foreground,
            modifier = Modifier.padding(top = 6.dp).testTag("learn:tense-portion-$lang"),
        )
    }
    Spacer(Modifier.height(6.dp))
    if (tense.notPossible != null) {
        Text(
            text = strings.notPossible,
            style = AppTheme.label,
            color = AppTheme.muted,
            modifier = Modifier.testTag("learn:tense-no-$lang"),
        )
        if (strings.readsRussian) {
            // The base marks English in it with backticks, for its markdown.
            Text(
                text = tense.notPossible.replace("`", ""),
                style = AppTheme.footnote,
                color = AppTheme.foreground,
                modifier = Modifier.padding(top = 4.dp).testTag("learn:tense-closed"),
            )
        }
    } else {
        Text(
            text = strings.tenseAbout(tense),
            style = AppTheme.footnote,
            color = AppTheme.muted,
            modifier = Modifier.testTag("learn:tense-about-$lang"),
        )
    }
}

/** The letter's page: all that is on the verb's own card, and what the signs in a transcription mean. */
@Composable
private fun AboutVerb(verb: Verb, strings: LearnStrings, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(verbColor(verb.word))
            // What this page does not take for itself does not go through to the feed under it.
            .pointerInput(Unit) {}
            .testTag("learn:about-page"),
    ) {
        TopArrow(strings.back, "learn:about-back", onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .mouseDragScrolls(scroll)
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            VerbDetails(verb, strings)
            AboutSection(strings.signsTitle, "learn:about-signs") {
                strings.signs.forEach { sign ->
                    Text(
                        text = sign,
                        style = AppTheme.footnote,
                        color = AppTheme.muted,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** What a verb is: how it is said, what it means, its forms and what is particular about it. */
@Composable
private fun VerbDetails(verb: Verb, strings: LearnStrings) {
    Text(
        text = verb.word,
        style = AppTheme.title,
        color = AppTheme.foreground,
        modifier = Modifier.testTag("learn:about-word"),
    )
    verb.transcription?.let { sound ->
        Text(
            text = stressed(sound, AppTheme.foreground),
            style = AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight),
            color = AppTheme.muted,
            modifier = Modifier.padding(top = 4.dp).testTag("learn:about-sound"),
        )
    }
    AboutSection(strings.meaningsTitle, "learn:about-meanings") {
        // In the base the groups of meanings are divided by semicolons: each is a line of its own here.
        Text(
            text = verb.meaning.split(';').joinToString("\n") { it.trim() },
            style = AppTheme.body,
            color = AppTheme.foreground,
            modifier = Modifier.testTag("learn:about-meaning"),
        )
    }
    if (verb.forms.isNotEmpty()) {
        AboutSection(strings.formsTitle, "learn:about-forms") {
            verb.forms.forEach { (kind, variants) -> FormRow(strings.form(kind), kind, variants) }
        }
    }
    if (verb.notes.isNotBlank()) {
        AboutSection(strings.notesTitle, "learn:about-notes") {
            Text(
                text = verb.notes,
                style = AppTheme.body,
                color = AppTheme.foreground,
                modifier = Modifier.testTag("learn:about-notes-text"),
            )
        }
    }
}

@Composable
internal fun AboutSection(title: String, tag: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = AppTheme.subtitle,
        color = AppTheme.foreground,
        modifier = Modifier.testTag(tag),
    )
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun FormRow(name: String, kind: FormKind, variants: List<WordForm>) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(text = name, style = AppTheme.footnote, color = AppTheme.muted)
        variants.forEachIndexed { index, variant ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.word,
                    style = AppTheme.body,
                    color = AppTheme.foreground,
                    modifier = Modifier.testTag("learn:form-${kind.key}-$index"),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stressed(variant.transcription, AppTheme.foreground),
                    style = AppTheme.body,
                    color = AppTheme.muted,
                    modifier = Modifier.testTag("learn:form-${kind.key}-$index-sound"),
                )
            }
        }
    }
}

@Composable
private fun Finish(text: String, strings: LearnStrings, onList: () -> Unit) {
    Centered {
        Line(text, SentenceStyle, "learn:done")
        Spacer(Modifier.height(28.dp))
        AppButton(text = strings.toList, onClick = onList, modifier = Modifier.testTag("learn:finish-list"))
    }
}

/** In the middle of the screen, and scrolling if it does not fit: a long phrase with its transcription, on a small phone. */
@Composable
private fun Centered(scroll: ScrollState = rememberScrollState(), content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Clear of the bars above and of the word below.
            .padding(start = 24.dp, end = 24.dp, top = ArrowRoom, bottom = 112.dp)
            .wrapContentHeight()
            .scrollsAsACard(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Line(text: String, style: TextStyle, tag: String) {
    val color = if (style.color != Color.Unspecified) style.color else AppTheme.foreground
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(tag),
    )
}

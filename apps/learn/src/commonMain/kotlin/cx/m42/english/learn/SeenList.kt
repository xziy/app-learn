package cx.m42.english.learn

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What part of its width a row has to be dragged by to go, when it is not flicked. */
private const val SwipeShare = 0.4f

/** A row let go this fast goes, however little it has moved. */
private val FlickSpeed = 800.dp

/** How long the button for all of them waits for its second press. */
private const val SureMillis = 4_000L

/**
 * The verbs gone through to their end, the latest on top. A row opens the feed of that verb alone, a swipe takes the
 * mark off it, and so does the button at the bottom for all of them.
 */
@Composable
internal fun SeenList(
    session: Session,
    strings: LearnStrings,
    read: suspend (String) -> Verb?,
    onOpen: (Verb) -> Unit,
    onUnsee: (String) -> Unit,
    onUnseeAll: () -> Unit,
    list: LazyListState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = ArrowRoom)
            .testTag("learn:seen"),
    ) {
        Text(
            text = strings.seenTitle,
            style = AppTheme.title,
            color = AppTheme.foreground,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = if (session.seen.isEmpty()) strings.seenNone else strings.seenHint,
            style = AppTheme.footnote,
            color = AppTheme.muted,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .testTag(if (session.seen.isEmpty()) "learn:seen-none" else "learn:seen-hint"),
        )
        LazyColumn(
            state = list,
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f)
                .fillMaxWidth()
                .mouseDragScrolls(list)
                .testTag("learn:seen-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(session.seen.asReversed(), key = { it }) { word ->
                SwipeAway(strings.unsee, Modifier.animateItem(), onGone = { onUnsee(word) }) {
                    val verb by produceState<Verb?>(null, word) { value = read(word) }
                    VerbRow(word, verb, "learn:seen-verb-$word", onOpen = { verb?.let(onOpen) })
                }
            }
        }
        if (session.seen.isNotEmpty()) UnseeAll(strings, onUnseeAll)
    }
}

/** All the marks go at once and cannot be got back, so it takes a second press, and forgets the first after a while. */
@Composable
private fun UnseeAll(strings: LearnStrings, onUnseeAll: () -> Unit) {
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(sure) {
        if (sure) {
            delay(SureMillis)
            sure = false
        }
    }
    AppButton(
        text = if (sure) strings.unseeAllSure else strings.unseeAll,
        onClick = { if (sure) onUnseeAll() else sure = true },
        variant = ButtonVariant.Outline,
        size = ButtonSize.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .testTag("learn:unsee-all"),
    )
}

/**
 * A row that goes when swiped off to either side, by a finger or a mouse. Up and down stay with the list: whichever
 * way the press moves first decides. Swiping cannot be done with a screen reader, so there it is an action.
 */
@Composable
private fun SwipeAway(hint: String, modifier: Modifier, onGone: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxWidth()
            .semantics { customActions = listOf(CustomAccessibilityAction(hint) { onGone(); true }) }
            .pointerInput(Unit) {
                val speed = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { speed.resetTracking() },
                    onDragEnd = {
                        val side = offset.value.sign
                        val flick = speed.calculateVelocity().x
                        val gone = abs(offset.value) > size.width * SwipeShare ||
                            (abs(flick) > FlickSpeed.toPx() && flick.sign == side)
                        scope.launch {
                            if (gone) {
                                offset.animateTo(side * size.width)
                                onGone()
                            } else {
                                offset.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    speed.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            },
    ) {
        if (offset.value != 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppTheme.foreground.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp),
                contentAlignment = if (offset.value < 0f) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(text = hint, style = AppTheme.footnote, color = AppTheme.foreground)
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

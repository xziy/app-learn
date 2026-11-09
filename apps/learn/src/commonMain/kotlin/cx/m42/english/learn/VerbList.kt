package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.ChoiceRow
import cx.m42.superizer.ui.components.IconButton

/** SVG paths over a 24×24 viewBox. */
private const val CROSS =
    "M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12Z"
private const val PLUS = "M19 13H13V19H11V13H5V11H11V5H13V11H19V13Z"

/** What the arrow at the top takes up: a list starts below it, so its rows do not run under the arrow. */
internal val ArrowRoom = 64.dp

/**
 * What scrolls follows a finger and the wheel and ignores a mouse's drag, while in the feed next to it a drag moves
 * the cards. So on a desktop the drag moves this too. A finger is left to it.
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

/**
 * What the language opens on: the theme, the verbs drawn for this round, each in its own colour, the way to the seen
 * ones, and the rest of the theme to add by hand. A row opens the feed at that verb; the button above them goes on
 * from where the person stopped.
 */
@Composable
internal fun VerbList(
    session: Session,
    strings: LearnStrings,
    canResume: Boolean,
    onTheme: (String) -> Unit,
    onGo: () -> Unit,
    onPick: () -> Unit,
    onOpen: (String) -> Unit,
    onChoose: (String) -> Unit,
    onUnchoose: (String) -> Unit,
    onSeen: () -> Unit,
    /** How many words the dictionary has as seen. */
    wordsSeen: Int,
    onDictionary: () -> Unit,
    list: LazyListState,
) {
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = ArrowRoom)
            .mouseDragScrolls(list)
            .testTag("learn:list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // With one theme there is nothing to choose from.
        if (session.themes.size > 1) {
            item { ThemeChoice(session, strings, onTheme) }
        }
        item {
            Text(text = strings.listTitle, style = AppTheme.title, color = AppTheme.foreground)
        }
        item {
            if (session.verbs.isEmpty()) {
                val allSeen = session.others.isEmpty()
                Text(
                    text = if (allSeen) strings.allSeen else strings.noneChosen,
                    style = AppTheme.body,
                    color = AppTheme.muted,
                    modifier = Modifier.testTag(if (allSeen) "learn:all-seen" else "learn:none-chosen"),
                )
            } else {
                AppButton(
                    text = if (canResume) strings.resume else strings.start,
                    onClick = onGo,
                    modifier = Modifier.fillMaxWidth().testTag("learn:go"),
                )
            }
        }
        if (session.others.isNotEmpty()) {
            item {
                AppButton(
                    text = strings.pick,
                    onClick = onPick,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Default,
                    modifier = Modifier.fillMaxWidth().testTag("learn:pick"),
                )
            }
        }
        items(session.verbs, key = { it.word }) { verb ->
            VerbRow(verb.word, verb, "learn:verb-${verb.word}", onOpen = { onOpen(verb.word) }) {
                IconButton(
                    onClick = { onUnchoose(verb.word) },
                    label = strings.remove,
                    modifier = Modifier.testTag("learn:remove-${verb.word}"),
                ) { tint -> AppIconGlyph(AppIcon.Path(CROSS), tint) }
            }
        }
        item { ListEntry(strings.seenTitle, session.seen.size, "learn:seen-open", onSeen) }
        item { ListEntry(strings.words.entry, wordsSeen, "learn:dict-open", onDictionary) }
        if (session.others.isNotEmpty()) {
            item {
                Text(
                    text = strings.addTitle,
                    style = AppTheme.subtitle,
                    color = AppTheme.foreground,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(session.others, key = { "add-$it" }) { word ->
                OtherRow(word, strings, onAdd = { onChoose(word) })
            }
        }
    }
}

@Composable
private fun ThemeChoice(session: Session, strings: LearnStrings, onTheme: (String) -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(text = strings.themesTitle, style = AppTheme.subtitle, color = AppTheme.foreground)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppTheme.foreground.copy(alpha = 0.08f)),
        ) {
            session.themes.forEach { theme ->
                Box(Modifier.testTag("learn:theme-${theme.name}")) {
                    ChoiceRow(strings.themeTitle(theme), selected = theme == session.theme) { onTheme(theme.name) }
                }
            }
        }
        strings.themeAbout(session.theme)?.let { about ->
            Text(
                text = about,
                style = AppTheme.footnote,
                color = AppTheme.muted,
                modifier = Modifier.padding(top = 8.dp).testTag("learn:theme-about"),
            )
        }
    }
}

/** The way to the verbs already gone through, or to the words seen. Shown with none too: that is where they will be. */
@Composable
private fun ListEntry(title: String, count: Int, tag: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.foreground.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.body,
            color = AppTheme.foreground,
            modifier = Modifier.weight(1f),
        )
        Text(text = "$count", style = AppTheme.body, color = AppTheme.muted)
    }
}

/**
 * A verb on its own colour: how it is said and what it means. [verb] is null while those are not read yet, and the
 * row keeps its height for them.
 */
@Composable
internal fun VerbRow(
    word: String,
    verb: Verb?,
    tag: String,
    onOpen: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(verbColor(word))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(tag)
            .padding(start = 16.dp, end = if (trailing == null) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = word, style = AppTheme.subtitle, color = AppTheme.foreground)
                Spacer(Modifier.width(8.dp))
                verb?.transcription?.let { sound ->
                    Text(
                        text = stressed(sound, AppTheme.foreground),
                        style = AppTheme.label,
                        color = AppTheme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = verb?.meaning.orEmpty(),
                style = AppTheme.footnote,
                color = AppTheme.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun OtherRow(word: String, strings: LearnStrings, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word,
            style = AppTheme.body,
            color = AppTheme.foreground,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onAdd,
            label = strings.add,
            modifier = Modifier.testTag("learn:add-$word"),
        ) { tint -> AppIconGlyph(AppIcon.Path(PLUS), tint) }
    }
}

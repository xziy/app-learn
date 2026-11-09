package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.IconButton
import kotlinx.coroutines.delay

/** Which words the dictionary lists. */
internal enum class WordFilter { Seen, Unknown, Known, All }

/**
 * The rows of the dictionary: of the words seen, the latest on top, or of all of them by the alphabet, as [query]
 * finds them. A key whose word is no longer in the dictionary has no row.
 */
internal fun dictionaryRows(lexicon: Lexicon, marks: WordMarks, filter: WordFilter, query: String): List<WordSense> {
    val latest = marks.seen.asReversed()
    val keys = when (filter) {
        WordFilter.Seen -> latest
        WordFilter.Unknown -> latest.filter { it !in marks.known }
        WordFilter.Known -> latest.filter { it in marks.known } + marks.known.filter { it !in marks.seenSet }.sorted()
        WordFilter.All -> null
    }
    return search(query, keys?.mapNotNull(lexicon::find) ?: lexicon.all)
}

/**
 * The words of the cards seen, to find one and mark it known. A row opens the word's page, the box at its end marks
 * it; at the bottom, what was seen can be forgotten, with a second press.
 */
@Composable
internal fun Dictionary(
    lexicon: Lexicon?,
    marks: WordMarks,
    strings: LearnStrings,
    query: String,
    onQuery: (String) -> Unit,
    filter: WordFilter,
    onFilter: (WordFilter) -> Unit,
    onOpen: (WordSense) -> Unit,
    onKnow: (String, Boolean) -> Unit,
    onForgetSeen: () -> Unit,
    list: LazyListState,
) {
    val words = strings.words
    val rows = remember(lexicon, marks, filter, query) {
        lexicon?.let { dictionaryRows(it, marks, filter, query) }.orEmpty()
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = ArrowRoom)
            .testTag("learn:dictionary"),
    ) {
        Text(text = words.title, style = AppTheme.title, color = AppTheme.foreground, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            text = words.summary(marks.seen.size, marks.known.size),
            style = AppTheme.footnote,
            color = AppTheme.muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).testTag("learn:dict-summary"),
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            AppTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = words.search,
                modifier = Modifier.testTag("learn:dict-search"),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordFilter.entries.forEach { option ->
                val title = when (option) {
                    WordFilter.Seen -> words.seen
                    WordFilter.Unknown -> words.unknown
                    WordFilter.Known -> words.known
                    WordFilter.All -> words.all
                }
                Chip(title, selected = option == filter, tag = "learn:filter-${option.name.lowercase()}") { onFilter(option) }
            }
        }
        val empty = when {
            lexicon == null -> words.loading
            lexicon.entries.isEmpty() -> words.noDictionary
            rows.isEmpty() && query.isNotBlank() -> words.noneFound
            rows.isEmpty() -> words.none
            else -> null
        }
        if (empty != null) {
            Text(
                text = empty,
                style = AppTheme.body,
                color = AppTheme.muted,
                modifier = Modifier.padding(16.dp).testTag("learn:dict-empty"),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .mouseDragScrolls(list)
                .testTag("learn:dict-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                WordRow(row, known = row.key in marks.known, words, onOpen = { onOpen(row) }, onKnow = { onKnow(row.key, it) })
            }
        }
        if (marks.seen.isNotEmpty()) ForgetSeen(words, onForgetSeen)
    }
}

@Composable
private fun Chip(title: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = AppTheme.label,
        color = if (selected) AppTheme.background else AppTheme.foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AppTheme.foreground else AppTheme.foreground.copy(alpha = 0.08f))
            .border(1.dp, AppTheme.foreground.copy(alpha = if (selected) 0f else 0.2f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A word in one of its senses: how it is said, what it means, in how many phrases, and whether it is known. */
@Composable
private fun WordRow(row: WordSense, known: Boolean, words: WordStrings, onOpen: () -> Unit, onKnow: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(verbColor(row.entry.word))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("learn:dict-${row.key}")
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = row.entry.word, style = AppTheme.subtitle, color = AppTheme.foreground)
                Spacer(Modifier.width(8.dp))
                row.entry.transcription?.let { sound ->
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
                text = row.sense.ru.joinToString(", "),
                style = AppTheme.footnote,
                color = AppTheme.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.entry.counts[row.sense.id]?.let { count ->
            Text(text = "$count", style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
        IconButton(
            onClick = { onKnow(!known) },
            label = if (known) words.doNotKnow else words.know,
            modifier = Modifier.testTag("learn:dict-know-${row.key}"),
        ) { tint -> Tick(known, tint) }
    }
}

/** What was seen goes at once and cannot be got back, so it takes a second press, and forgets the first after a while. */
@Composable
private fun ForgetSeen(words: WordStrings, onForget: () -> Unit) {
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(sure) {
        if (sure) {
            delay(4_000L)
            sure = false
        }
    }
    AppButton(
        text = if (sure) words.forgetSeenSure else words.forgetSeen,
        onClick = { if (sure) onForget().also { sure = false } else sure = true },
        variant = ButtonVariant.Outline,
        size = ButtonSize.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .testTag("learn:dict-forget"),
    )
}

package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.IconButton

/** What a word's page is opened on: the word, the sense it is in where it was met, and what it means there. */
internal data class WordOpen(val word: String, val sense: String?, val meaning: String? = null) {
    constructor(use: NounUse) : this(use.word, use.sense, use.meaning)
    constructor(row: WordSense) : this(row.entry.word, row.sense.id)
}

/** A tick, as an SVG path over a 24×24 viewBox. */
internal const val TICK = "M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59Z"

/** The tag a sense goes by in tests: its id, or `main`. */
private fun Sense.tag(): String = id ?: "main"

/**
 * A word's page, over what it was opened from, which stays where it was: what the dictionary knows about the word,
 * a mark «I know it» on each sense, and every phrase it stands in. A phrase opens the feed of its verb on it.
 */
@Composable
internal fun WordPage(
    open: WordOpen,
    lexicon: Lexicon?,
    marks: WordMarks,
    themes: List<Theme>,
    strings: LearnStrings,
    canSpeak: Boolean,
    onSay: (String) -> Unit,
    onKnow: (String, Boolean) -> Unit,
    read: suspend (theme: String, verb: String) -> Verb?,
    onPhrase: (Verb, Sentence) -> Unit,
    onBack: () -> Unit,
) {
    val words = strings.words
    val entry = lexicon?.entry(open.word)
    // Without the word in the dictionary the page still has what the phrase says about it.
    val shown = entry ?: Entry(open.word, listOf(Sense(open.sense, listOfNotNull(open.meaning))))
    val senses = shown.senses.sortedBy { if (it.id == open.sense) 0 else 1 }
    val list = rememberLazyListState()
    Column(
        Modifier
            .fillMaxSize()
            .background(verbColor(open.word))
            // What this page does not take for itself does not go through to what is under it.
            .pointerInput(Unit) {}
            .testTag("learn:word-page"),
    ) {
        TopArrow(strings.back, "learn:word-back", onBack)
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .mouseDragScrolls(list)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("learn:word-list"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            item { Heading(shown, words, canSpeak, onSay) }
            item { NumberLine(shown, words) }
            items(senses, key = { "sense-${it.tag()}" }) { sense ->
                SenseBlock(
                    sense = sense,
                    themes = themes,
                    strings = strings,
                    inPhrase = open.meaning != null && sense.id == open.sense && shown.senses.size > 1,
                    known = shown.key(sense) in marks.known,
                    onKnow = { onKnow(shown.key(sense), it) },
                )
            }
            if (entry != null && !entry.filled) {
                item { Footnote(words.notFilled, "learn:word-not-filled") }
            }
            if (shown.family.isNotEmpty()) {
                item {
                    AboutSection(words.family, "learn:word-family") {
                        shown.family.forEach { kin ->
                            val kind = words.kinds[kin.kind] ?: kin.kind
                            Text(
                                text = "${kin.word} · $kind — ${kin.ru}",
                                style = AppTheme.body,
                                color = AppTheme.foreground,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (shown.notes.isNotBlank()) {
                item {
                    AboutSection(words.notes, "learn:word-notes") {
                        Text(text = shown.notes, style = AppTheme.body, color = AppTheme.foreground, modifier = Modifier.testTag("learn:word-notes-text"))
                    }
                }
            }
            if (entry != null && entry.places.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = words.uses(entry.phrases),
                        style = AppTheme.subtitle,
                        color = AppTheme.foreground,
                        modifier = Modifier.testTag("learn:word-uses"),
                    )
                }
                // In the order of the themes, each by its verbs; a verb is read when its row comes on screen.
                val byTheme = themes.filter { it.name in entry.places } + entry.places.keys.filter { name -> themes.none { it.name == name } }.map { Theme(it, it) }
                for (theme in byTheme) {
                    if (byTheme.size > 1) {
                        item(key = "theme-${theme.name}") {
                            Text(
                                text = strings.themeTitle(theme),
                                style = AppTheme.label,
                                color = AppTheme.muted,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(entry.places.getValue(theme.name), key = { "use-${theme.name}-$it" }) { verb ->
                        UsesInVerb(theme.name, verb, entry, lexicon, read, onPhrase)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(entry: Entry, words: WordStrings, canSpeak: Boolean, onSay: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = entry.word,
            style = AppTheme.title,
            color = AppTheme.foreground,
            modifier = Modifier.weight(1f, fill = false).testTag("learn:word-title"),
        )
        if (canSpeak) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onSay(entry.word) }, label = words.say, modifier = Modifier.testTag("learn:word-say")) { tint ->
                AppIconGlyph(AppIcon.Path(SPEAKER), tint, size = 24.dp)
            }
        }
    }
    entry.transcription?.let { sound ->
        Text(
            text = stressed(sound, AppTheme.foreground),
            style = AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight),
            color = AppTheme.muted,
            modifier = Modifier.padding(top = 4.dp).testTag("learn:word-sound"),
        )
    }
}

/** The plural, and what is particular about the number of the word. */
@Composable
private fun NumberLine(entry: Entry, words: WordStrings) {
    val about = when {
        entry.number == NounNumber.PluralOnly -> words.pluralOnly
        entry.number == NounNumber.Same -> words.same
        entry.countable == Countable.No -> words.uncountable
        entry.countable == Countable.Both -> words.both
        else -> null
    }
    val collective = if (entry.number == NounNumber.Collective) words.collective else null
    if (entry.plural.isEmpty() && about == null && collective == null) return
    Column(Modifier.padding(top = 12.dp)) {
        if (entry.plural.isNotEmpty()) {
            Text(
                text = buildAnnotatedString {
                    append("${words.plural}: ")
                    entry.plural.forEachIndexed { index, form ->
                        if (index > 0) append(", ")
                        withStyle(SpanStyle(color = AppTheme.foreground)) { append(form.word) }
                        if (form.transcription.isNotEmpty()) {
                            append(" ")
                            append(stressed(form.transcription, AppTheme.foreground))
                        }
                    }
                },
                style = AppTheme.body,
                color = AppTheme.muted,
                modifier = Modifier.testTag("learn:word-plural"),
            )
        }
        listOfNotNull(about, collective).forEach { Footnote(it, "learn:word-number") }
    }
}

@Composable
private fun SenseBlock(
    sense: Sense,
    themes: List<Theme>,
    strings: LearnStrings,
    inPhrase: Boolean,
    known: Boolean,
    onKnow: (Boolean) -> Unit,
) {
    val words = strings.words
    Column(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.foreground.copy(alpha = if (inPhrase) 0.12f else 0.06f))
            .padding(16.dp)
            .testTag("learn:sense-${sense.tag()}"),
    ) {
        if (inPhrase) Footnote(words.inPhrase, "learn:sense-in-phrase")
        Text(
            text = sense.ru.joinToString(", "),
            style = AppTheme.subtitle,
            color = AppTheme.foreground,
            modifier = Modifier.testTag("learn:sense-${sense.tag()}-ru"),
        )
        val ofThemes = sense.themes.map { name -> themes.find { it.name == name }?.let(strings::themeTitle) ?: name }
        if (ofThemes.isNotEmpty()) Footnote(ofThemes.joinToString(" · "), "learn:sense-${sense.tag()}-themes")
        sense.en?.let { Text(text = it, style = AppTheme.body, color = AppTheme.muted, modifier = Modifier.padding(top = 4.dp)) }
        RelatedWords(words.synonyms, sense.synonyms, "learn:sense-${sense.tag()}-synonyms")
        RelatedWords(words.antonyms, sense.antonyms, "learn:sense-${sense.tag()}-antonyms")
        if (sense.collocations.isNotEmpty()) {
            Text(text = words.collocations, style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.padding(top = 10.dp))
            sense.collocations.forEach {
                Text(text = "· $it", style = AppTheme.body, color = AppTheme.foreground, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        KnowToggle(known, words, "learn:know-${sense.tag()}", onKnow)
    }
}

@Composable
private fun RelatedWords(title: String, related: List<Related>, tag: String) {
    if (related.isEmpty()) return
    Text(text = title, style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.padding(top = 10.dp))
    related.forEach { word ->
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = AppTheme.foreground)) { append(word.word) }
                word.note?.let { append(" — $it") }
            },
            style = AppTheme.body,
            color = AppTheme.muted,
            modifier = Modifier.padding(top = 2.dp).testTag("$tag-${word.word}"),
        )
    }
}

/** «I know it»: a box that is ticked or not, the whole row being the switch. */
@Composable
internal fun KnowToggle(known: Boolean, words: WordStrings, tag: String, onKnow: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = known, role = Role.Checkbox, onValueChange = onKnow)
            .testTag(tag)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tick(known, AppTheme.foreground)
        Spacer(Modifier.width(10.dp))
        Text(text = words.know, style = AppTheme.body, color = AppTheme.foreground)
    }
}

@Composable
internal fun Tick(on: Boolean, tint: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, tint.copy(alpha = if (on) 1f else 0.6f), RoundedCornerShape(6.dp))
            .background(if (on) tint else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (on) AppIconGlyph(AppIcon.Path(TICK), AppTheme.background, size = 18.dp)
    }
}

@Composable
private fun Footnote(text: String, tag: String) {
    Text(text = text, style = AppTheme.footnote, color = AppTheme.muted, modifier = Modifier.padding(top = 4.dp).testTag(tag))
}

/** The phrases of one verb that have the word, with the word set off. Read when the row comes on screen. */
@Composable
private fun UsesInVerb(
    theme: String,
    verb: String,
    entry: Entry,
    lexicon: Lexicon?,
    read: suspend (theme: String, verb: String) -> Verb?,
    onPhrase: (Verb, Sentence) -> Unit,
) {
    val loaded by produceState<Verb?>(null, theme, verb) { value = read(theme, verb) }
    val found = loaded ?: run {
        Text(text = verb, style = AppTheme.label, color = AppTheme.muted, modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    val phrases = remember(found, entry.word) {
        found.tenses.flatMap { it.sentences }.filter { sentence -> sentence.nouns.any { it.word == entry.word } }
    }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = verb, style = AppTheme.label, color = AppTheme.muted)
        phrases.forEach { sentence ->
            val marks = remember(sentence) {
                nounMarks(sentence.english, sentence.nouns.filter { it.word == entry.word }) { lexicon?.spellings(it) ?: setOf(it.lowercase()) }
            }
            Text(
                text = buildAnnotatedString {
                    var at = 0
                    for (mark in marks) {
                        append(sentence.english.substring(at, mark.start))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppTheme.foreground)) {
                            append(sentence.english.substring(mark.start, mark.end))
                        }
                        at = mark.end
                    }
                    append(sentence.english.substring(at))
                },
                style = AppTheme.body,
                color = AppTheme.foreground.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.foreground.copy(alpha = 0.06f))
                    .clickable(role = Role.Button) { onPhrase(found, sentence) }
                    .testTag("learn:use-$verb-${sentence.address}")
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

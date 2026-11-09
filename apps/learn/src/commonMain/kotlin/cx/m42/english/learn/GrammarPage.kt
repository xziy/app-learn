package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppIconGlyph
import androidx.compose.ui.semantics.Role as SemanticsRole

/** A graduation cap over a 24×24 viewBox: the board, the cap under it, and the tassel. */
private const val CAP =
    "M12 3L1 8.5L12 14L23 8.5Z" +
        "M5.5 11.8V16.5C5.5 18.4 8.4 20 12 20C15.6 20 18.5 18.4 18.5 16.5V11.8L12 15.1Z" +
        "M20.2 9.9H21.2V15H20.2Z" +
        "M19.5 16.2A1.2 1.2 0 1 0 21.9 16.2A1.2 1.2 0 1 0 19.5 16.2Z"

/** The colours of the parts of a formula, the same in a formula and in the phrase laid along it. Light, on a dark card. */
private fun roleColor(role: Role?): Color? = when (role) {
    Role.QuestionWord -> Color(0xFFD9B8FF)
    Role.Subject -> Color(0xFF9FD0FF)
    Role.Auxiliary -> Color(0xFFFFD27A)
    Role.Not -> Color(0xFFFF9A8F)
    Role.Adverb -> Color(0xFFC9D2DE)
    Role.Verb -> Color(0xFF9EF0B0)
    null -> null
}

@Composable
private fun roleStyle(role: Role?): SpanStyle =
    SpanStyle(color = roleColor(role) ?: AppTheme.foreground, fontWeight = if (role != null) FontWeight.Bold else null)

/**
 * Which form the phrase on screen is in, as one quiet line at the top between the arrows, the way the word is at the
 * bottom: the cap, `Тип 1 · Прошедшее · Вопрос`, and `Актив · Past Simple` dimmer after it. Where the line does not fit,
 * the dimmer part gives way first. The whole line is the button that opens how that form is built.
 */
@Composable
internal fun FormLine(entry: FeedPage.Phrase, strings: LearnStrings, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val kind = remember(entry) { parsePhrase(entry.sentence.english, entry.verb, entry.tense).kind }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = SemanticsRole.Button, onClickLabel = strings.grammar.open, onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("learn:form"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconGlyph(AppIcon.Path(CAP), AppTheme.muted, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = strings.grammar.formLine(entry.tense, kind, strings),
            style = AppTheme.label,
            color = AppTheme.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = strings.grammar.voiceLine(entry.tense, strings),
            style = AppTheme.label,
            color = AppTheme.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * The cap's page, over the feed as the letter's is, in the app's language. It is for someone who knows nothing yet, so it
 * goes from the phrase to the rule: what this tense and kind are for, the phrase in parts with what each is, its formula
 * with every sign explained, what to keep in mind, the other kinds with a phrase of this verb, and the words the page uses.
 */
@Composable
internal fun RulePage(entry: FeedPage.Phrase, strings: LearnStrings, onBack: () -> Unit) {
    val grammar = strings.grammar
    val texts = grammar.page
    val verb = entry.verb
    val tense = entry.tense
    val sentence = entry.sentence
    val phrase = remember(entry) { parsePhrase(sentence.english, verb, tense) }
    val formula = remember(entry, phrase.kind) { tense.formula(phrase.kind, verb.word) }
    val parts = remember(entry, texts) { if (phrase.hasVerb) texts.parts(phrase, verb, tense) else emptyList() }
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(verbColor(verb.word))
            // What this page does not take for itself does not go through to the feed under it.
            .pointerInput(Unit) {}
            .testTag("learn:rule-page"),
    ) {
        TopArrow(strings.back, "learn:rule-back", onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .mouseDragScrolls(scroll)
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            Text(
                text = grammar.formLine(tense, phrase.kind, strings),
                style = AppTheme.title,
                color = AppTheme.foreground,
                modifier = Modifier.testTag("learn:rule-title"),
            )
            Text(
                text = grammar.voiceLine(tense, strings),
                style = AppTheme.label,
                color = AppTheme.muted,
                modifier = Modifier.padding(top = 4.dp).testTag("learn:rule-voice"),
            )
            Text(
                text = texts.about(tense, phrase.kind),
                style = AppTheme.body,
                color = AppTheme.foreground,
                modifier = Modifier.padding(top = 12.dp).testTag("learn:rule-about"),
            )

            AboutSection(texts.phraseTitle, "learn:rule-phrase") {
                Text(
                    text = laidOut(phrase),
                    style = AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight),
                    color = AppTheme.foreground,
                    modifier = Modifier.testTag("learn:rule-sentence"),
                )
                Caption(texts.translation)
                Text(
                    text = sentence.ours,
                    style = AppTheme.body,
                    color = AppTheme.foreground,
                    modifier = Modifier.testTag("learn:rule-phrase-ours"),
                )
                Caption(strings.thought)
                Text(
                    text = sentence.thought,
                    style = AppTheme.body,
                    color = AppTheme.foreground,
                    modifier = Modifier.testTag("learn:rule-phrase-thought"),
                )
                Text(
                    text = texts.thoughtAbout,
                    style = AppTheme.footnote,
                    color = AppTheme.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Caption(texts.partsTitle)
                if (parts.isEmpty()) {
                    Text(text = texts.noBreakdown, style = AppTheme.footnote, color = AppTheme.muted, modifier = Modifier.testTag("learn:rule-no-parts"))
                }
                parts.forEachIndexed { index, part ->
                    Explained(part.text, part.role, part.about, Modifier.testTag("learn:rule-part-$index"))
                }
            }

            if (formula != null) {
                AboutSection(texts.formulaTitle, "learn:rule-formula") {
                    FormulaLines(formula, tense, texts, tag = "learn:rule")
                    Spacer(Modifier.height(8.dp))
                    texts.legend(formula, verb, tense).forEach { sign ->
                        Explained(sign.sign, sign.role, sign.text, Modifier.testTag("learn:rule-${sign.key}"))
                    }
                }
            }

            AboutSection(texts.rulesTitle, "learn:rule-rules") {
                texts.rules(phrase, tense, verb.word, sentence.english).forEachIndexed { index, rule ->
                    Row(Modifier.padding(vertical = 3.dp).testTag("learn:rule-$index")) {
                        Text(text = "•", style = AppTheme.body, color = AppTheme.muted)
                        Spacer(Modifier.width(8.dp))
                        Text(text = rule, style = AppTheme.body, color = AppTheme.foreground)
                    }
                }
            }

            val others = tense.kinds.filter { it != phrase.kind }.mapNotNull { kind -> tense.formula(kind, verb.word) }
            if (others.isNotEmpty()) {
                // A phrase of this verb in this tense for each kind, where the base has one.
                val examples = remember(entry) {
                    tense.sentences.filter { it != sentence }.groupBy { parsePhrase(it.english, verb, tense).kind }
                }
                AboutSection(texts.othersTitle(others.map { grammar.kinds.getValue(it.kind) }), "learn:rule-others") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        others.forEach { other ->
                            val tag = "learn:rule-other-${other.kind.name.lowercase()}"
                            Column {
                                Text(text = grammar.kinds.getValue(other.kind), style = AppTheme.label, color = AppTheme.muted)
                                FormulaLines(other, tense, texts, tag = tag, small = true)
                                examples[other.kind]?.firstOrNull()?.let { example ->
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(example.english) }
                                            withStyle(SpanStyle(color = AppTheme.muted)) { append(" — ${example.ours}") }
                                        },
                                        style = AppTheme.body,
                                        color = AppTheme.foreground,
                                        modifier = Modifier.padding(top = 4.dp).testTag("$tag-example"),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AboutSection(texts.glossaryTitle, "learn:rule-glossary") {
                texts.glossary.forEach { (term, text) ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(term) }
                            append(" — $text")
                        },
                        style = AppTheme.body,
                        color = AppTheme.foreground,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** A formula in English and in the thought, one under the other. The thought's, `кто` too, is Russian in any language. */
@Composable
private fun FormulaLines(formula: Formula, tense: Tense, texts: RuleTexts, tag: String, small: Boolean = false) {
    val style = if (small) AppTheme.body else AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight)
    if (!small) Caption(texts.inEnglish)
    Text(
        text = formulaText(formula.english, texts.subjectOf(tense), formula.question),
        style = style,
        color = AppTheme.foreground,
        modifier = Modifier.testTag("$tag-english"),
    )
    if (!small) Caption(texts.thoughtOnWash)
    Text(
        text = formulaText(formula.thought, RussianRules.subjectOf(tense), formula.question),
        style = style,
        color = AppTheme.foreground,
        modifier = Modifier.padding(top = if (small) 2.dp else 0.dp).testTag("$tag-thought"),
    )
}

/** `Did + кто + V + …?`, each part in its colour. */
@Composable
private fun formulaText(slots: List<Slot>, subject: String, question: Boolean): AnnotatedString {
    val plus = SpanStyle(color = AppTheme.muted)
    val styles = Role.entries.associateWith { roleStyle(it) }
    val plain = roleStyle(null)
    return buildAnnotatedString {
        slots.forEachIndexed { index, slot ->
            if (index > 0) withStyle(plus) { append(" + ") }
            val text = when (slot) {
                is Slot.Word -> slot.text
                Slot.Subject -> subject
                is Slot.Form -> slot.kinds.joinToString(" / ") { it.symbol() }
            }
            withStyle(slot.role?.let(styles::getValue) ?: plain) { append(text) }
        }
        withStyle(plus) { append(" + …") }
        if (question) append("?")
    }
}

/** The phrase in the colours of its formula. */
@Composable
private fun laidOut(phrase: Breakdown): AnnotatedString {
    val styles = Role.entries.associateWith { roleStyle(it) }
    return buildAnnotatedString {
        phrase.pieces.forEach { piece ->
            val role = piece.role
            if (role == null) append(piece.text) else withStyle(styles.getValue(role)) { append(piece.text) }
        }
    }
}

/** `didn't — это did not: …`: a word in the colour of its role, and what it is. */
@Composable
private fun Explained(word: String, role: Role?, about: String, modifier: Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(roleStyle(role).copy(fontWeight = FontWeight.Bold)) { append(word) }
            withStyle(SpanStyle(color = AppTheme.muted)) { append(" — ") }
            append(about)
        },
        style = AppTheme.body,
        color = AppTheme.foreground,
        modifier = modifier.padding(vertical = 3.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = AppTheme.footnote,
        color = AppTheme.muted,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

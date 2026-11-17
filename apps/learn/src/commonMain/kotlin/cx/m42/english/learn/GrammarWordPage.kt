package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.composeunstyled.Text
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppIconGlyph
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.components.IconButton

/** What the page of an irregular verb or a service word is opened on, and the phrase it was met in, if any. */
internal sealed interface GrammarOpen {
    val key: String

    data class Irregular(val verb: String, val form: String? = null, val phrase: FeedPage.Phrase? = null) : GrammarOpen {
        override val key: String get() = verbKey(verb)
    }

    data class Service(
        val word: String,
        val form: String? = null,
        /** Part of the formula of [phrase]'s tense there: the page then says which tense, and opens its rule. */
        val inFormula: Boolean = false,
        val phrase: FeedPage.Phrase? = null,
    ) : GrammarOpen {
        override val key: String get() = serviceKey(word)
    }
}

/** The page a link of D25 opens. A noun has a page of its own, [WordPage]. */
internal fun GrammarOpen(link: WordLink, phrase: FeedPage.Phrase?): GrammarOpen? = when (link) {
    is VerbLink -> GrammarOpen.Irregular(link.word, link.form, phrase)
    is ServiceLink -> GrammarOpen.Service(link.word, link.form, link.inFormula, phrase)
    is NounUse -> null
}

/** The words of these pages and of their filters in the dictionary, one table per language. */
internal data class GrammarWordStrings(
    val irregularFilter: String,
    val serviceFilter: String,
    val irregular: String,
    val irregularAbout: String,
    val threeForms: String,
    /** `went — прошедшее время`. */
    val formHere: (form: String, name: String) -> String,
    val classNames: Map<WordClass, String>,
    val also: String,
    val standsFor: String,
    val alsoSaid: String,
    val thought: String,
    val notSaid: String,
    /** `Здесь это часть времени: Тип 1 · Прошедшее · Вопрос`. */
    val inFormula: (String) -> String,
    val openRule: String,
    val notInTable: String,
) {
    companion object {
        val Ru = GrammarWordStrings(
            irregularFilter = "Неправильные глаголы",
            serviceFilter = "Служебные слова",
            irregular = "Неправильный глагол",
            irregularAbout = "Прошедшее время и пассивное определение у него строятся не через -ed, их надо помнить. " +
                "Звёздочка над ним во фразе остаётся и после «Знаю»: она говорит, что глагол неправильный.",
            threeForms = "Три формы",
            formHere = { form, name -> "$form — ${name.lowercase()}" },
            classNames = mapOf(
                WordClass.Auxiliary to "Вспомогательный глагол",
                WordClass.Modal to "Модальный глагол",
                WordClass.Article to "Артикль",
                WordClass.Determiner to "Определитель",
                WordClass.Pronoun to "Местоимение",
                WordClass.Possessive to "Притяжательное местоимение",
                WordClass.Reflexive to "Возвратное местоимение",
                WordClass.Preposition to "Предлог",
                WordClass.Conjunction to "Союз",
                WordClass.Question to "Вопросительное слово",
                WordClass.Particle to "Частица",
            ),
            also = "Бывает и",
            standsFor = "Сокращение от",
            alsoSaid = "Ещё произносят",
            thought = "В мысли англичанина",
            notSaid = "Не звучит",
            inFormula = { "Здесь это часть времени: $it" },
            openRule = "Как строится это время",
            notInTable = "Этого слова ещё нет в таблице служебных слов",
        )

        val En = GrammarWordStrings(
            irregularFilter = "Irregular verbs",
            serviceFilter = "Service words",
            irregular = "Irregular verb",
            irregularAbout = "Its past and its participle are not made with -ed: they have to be remembered. " +
                "The star over it in a phrase stays after «I know it»: it says that the verb is irregular.",
            threeForms = "Three forms",
            formHere = { form, name -> "$form — ${name.lowercase()}" },
            classNames = mapOf(
                WordClass.Auxiliary to "Auxiliary verb",
                WordClass.Modal to "Modal verb",
                WordClass.Article to "Article",
                WordClass.Determiner to "Determiner",
                WordClass.Pronoun to "Pronoun",
                WordClass.Possessive to "Possessive",
                WordClass.Reflexive to "Reflexive pronoun",
                WordClass.Preposition to "Preposition",
                WordClass.Conjunction to "Conjunction",
                WordClass.Question to "Question word",
                WordClass.Particle to "Particle",
            ),
            also = "Also",
            standsFor = "Short for",
            alsoSaid = "Also said",
            thought = "In the English thought",
            notSaid = "Not said",
            inFormula = { "Here it is part of the tense: $it" },
            openRule = "How this tense is built",
            notInTable = "The table of service words does not have this word yet",
        )
    }
}

/** An irregular verb as the star shows it: `go*`. */
internal fun starred(word: String) = buildAnnotatedString {
    append(word)
    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.6.em)) { append(IrregularStar) }
}

/**
 * The page of an irregular verb or a service word, over what it was opened from, as a noun's page is: how it is said,
 * what it is, «I know it», and for a service word how the thought line has it and, where it builds the phrase's tense,
 * which tense that is, with the way to its rule.
 */
@Composable
internal fun GrammarWordPage(
    open: GrammarOpen,
    words: GrammarWords,
    marks: WordMarks,
    strings: LearnStrings,
    canSpeak: Boolean,
    onSay: (String) -> Unit,
    onKnow: (String, Boolean) -> Unit,
    onRule: (FeedPage.Phrase) -> Unit,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    val title = when (open) {
        is GrammarOpen.Irregular -> open.verb
        is GrammarOpen.Service -> open.word
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(verbColor(title))
            // What this page does not take for itself does not go through to what is under it.
            .pointerInput(Unit) {}
            .testTag("learn:grammar-page"),
    ) {
        TopArrow(strings.back, "learn:grammar-back", onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .mouseDragScrolls(scroll)
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            when (open) {
                is GrammarOpen.Irregular -> IrregularDetails(open, words, marks, strings, canSpeak, onSay, onKnow)
                is GrammarOpen.Service -> ServiceDetails(open, words, marks, strings, canSpeak, onSay, onKnow, onRule)
            }
        }
    }
}

@Composable
private fun TitleLine(title: androidx.compose.ui.text.AnnotatedString, say: String?, strings: LearnStrings, onSay: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = AppTheme.title, color = AppTheme.foreground, modifier = Modifier.testTag("learn:grammar-word"))
        if (say != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { onSay(say) }, label = strings.words.say, modifier = Modifier.testTag("learn:grammar-say")) { tint ->
                AppIconGlyph(AppIcon.Path(SPEAKER), tint, size = 22.dp)
            }
        }
    }
}

@Composable
private fun Sound(transcription: String?, tag: String) {
    transcription?.let { sound ->
        Text(
            text = stressed(sound, AppTheme.foreground),
            style = AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight),
            color = AppTheme.muted,
            modifier = Modifier.padding(top = 4.dp).testTag(tag),
        )
    }
}

@Composable
private fun Body(text: String, tag: String, muted: Boolean = false) {
    Text(
        text = text,
        style = AppTheme.body,
        color = if (muted) AppTheme.muted else AppTheme.foreground,
        modifier = Modifier.padding(vertical = 2.dp).testTag(tag),
    )
}

@Composable
private fun IrregularDetails(
    open: GrammarOpen.Irregular,
    words: GrammarWords,
    marks: WordMarks,
    strings: LearnStrings,
    canSpeak: Boolean,
    onSay: (String) -> Unit,
    onKnow: (String, Boolean) -> Unit,
) {
    val texts = strings.words.grammarWords
    // The verb's own forms where the phrase is of it: they are what its cards say.
    val ofPhrase = open.phrase?.verb?.takeIf { it.word == open.verb && it.forms.isNotEmpty() }
    val table = words.verb(open.verb)
    val verb = when {
        ofPhrase != null -> IrregularVerb(open.verb, table?.ru ?: ofPhrase.meaning, ofPhrase.forms)
        else -> table ?: IrregularVerb(open.verb, "", emptyMap())
    }
    TitleLine(starred(verb.verb), if (canSpeak) verb.threeForms.replace(" — ", ", ") else null, strings, onSay)
    Sound(verb.transcription, "learn:grammar-sound")
    Body(texts.irregular, "learn:grammar-class", muted = true)
    if (verb.ru.isNotBlank()) Body(verb.ru, "learn:grammar-ru")
    Spacer(Modifier.padding(top = 8.dp))
    KnowToggle(verb.key in marks.known, strings.words, "learn:grammar-know") { onKnow(verb.key, it) }
    AboutSection(texts.threeForms, "learn:grammar-three") {
        Text(text = verb.threeForms, style = AppTheme.subtitle, color = AppTheme.foreground, modifier = Modifier.testTag("learn:grammar-three-forms"))
    }
    val here = open.form?.let { form -> verb.formOf(form)?.let { kind -> texts.formHere(form, strings.form(kind)) } }
    if (here != null) {
        AboutSection(strings.words.inPhrase, "learn:grammar-here") { Body(here, "learn:grammar-here-text") }
    }
    if (verb.forms.isNotEmpty()) {
        AboutSection(strings.formsTitle, "learn:grammar-forms") {
            verb.forms.forEach { (kind, variants) ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(text = strings.form(kind), style = AppTheme.footnote, color = AppTheme.muted)
                    variants.forEachIndexed { index, variant ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = variant.word,
                                style = AppTheme.body,
                                color = AppTheme.foreground,
                                modifier = Modifier.testTag("learn:grammar-form-${kind.key}-$index"),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(text = stressed(variant.transcription, AppTheme.foreground), style = AppTheme.body, color = AppTheme.muted)
                        }
                    }
                }
            }
        }
    }
    AboutSection(strings.notesTitle, "learn:grammar-notes") { Body(texts.irregularAbout, "learn:grammar-about") }
}

@Composable
private fun ServiceDetails(
    open: GrammarOpen.Service,
    words: GrammarWords,
    marks: WordMarks,
    strings: LearnStrings,
    canSpeak: Boolean,
    onSay: (String) -> Unit,
    onKnow: (String, Boolean) -> Unit,
    onRule: (FeedPage.Phrase) -> Unit,
) {
    val texts = strings.words.grammarWords
    val word = words.service(open.word)
    TitleLine(buildAnnotatedString { append(word?.word ?: open.word) }, if (canSpeak) (word?.word ?: open.word) else null, strings, onSay)
    Sound(word?.transcription, "learn:grammar-sound")
    word?.variants?.forEach { variant ->
        Body("${texts.alsoSaid}: ${variant.transcription}" + if (variant.note.isNotBlank()) " — ${variant.note}" else "", "learn:grammar-variant", muted = true)
    }
    if (word == null) {
        Body(texts.notInTable, "learn:grammar-not-in-table", muted = true)
    } else {
        val classes = buildString {
            append(texts.classNames[word.wordClass] ?: word.wordClass.key)
            if (word.also.isNotEmpty()) {
                append(" · ${texts.also.lowercase()}: ")
                append(word.also.joinToString(", ") { (texts.classNames[it] ?: it.key).lowercase() })
            }
        }
        Body(classes, "learn:grammar-class", muted = true)
        if (word.parts.isNotEmpty()) Body("${texts.standsFor} ${word.parts.joinToString(" ")}", "learn:grammar-parts")
        if (word.ru.isNotBlank()) Body(word.ru, "learn:grammar-ru")
    }
    Spacer(Modifier.padding(top = 8.dp))
    val key = word?.key ?: serviceKey(open.word)
    KnowToggle(key in marks.known, strings.words, "learn:grammar-know") { onKnow(key, it) }
    val phrase = open.phrase
    if (open.inFormula && phrase != null) {
        val kind = remember(phrase) { parsePhrase(phrase.sentence.english, phrase.verb, phrase.tense).kind }
        AboutSection(strings.words.inPhrase, "learn:grammar-here") {
            Body(texts.inFormula(strings.grammar.formLine(phrase.tense, kind, strings)), "learn:grammar-here-text")
            Spacer(Modifier.padding(top = 4.dp))
            AppButton(
                text = texts.openRule,
                onClick = { onRule(phrase) },
                variant = ButtonVariant.Outline,
                modifier = Modifier.testTag("learn:grammar-rule"),
            )
        }
    }
    if (word != null) {
        AboutSection(texts.thought, "learn:grammar-thought") {
            Body(word.thought.ifBlank { texts.notSaid }, "learn:grammar-thought-text")
        }
        if (word.about.isNotBlank()) {
            AboutSection(strings.notesTitle, "learn:grammar-notes") { Body(word.about, "learn:grammar-about") }
        }
    }
}

/** A row of the dictionary for an irregular verb or a service word: the word, how it is said, what it is, and «I know it». */
@Composable
internal fun GrammarRow(
    title: androidx.compose.ui.text.AnnotatedString,
    transcription: String?,
    subtitle: String,
    key: String,
    known: Boolean,
    words: WordStrings,
    onOpen: () -> Unit,
    onKnow: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(verbColor(title.text))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("learn:dict-$key")
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = AppTheme.subtitle, color = AppTheme.foreground)
                Spacer(Modifier.width(8.dp))
                transcription?.let { sound ->
                    Text(
                        text = stressed(sound, AppTheme.foreground),
                        style = AppTheme.label,
                        color = AppTheme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(text = subtitle, style = AppTheme.footnote, color = AppTheme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = { onKnow(!known) },
            label = if (known) words.doNotKnow else words.know,
            modifier = Modifier.testTag("learn:dict-know-$key"),
        ) { tint -> Tick(known, tint) }
    }
}

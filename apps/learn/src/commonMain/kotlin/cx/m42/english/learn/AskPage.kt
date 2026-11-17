package cx.m42.english.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonVariant

/**
 * Three nodes joined by two lines, over a 24×24 viewBox: the sign for sharing on most phones. Every part goes the same
 * way round, so where they overlap they stay filled.
 */
internal const val SHARE =
    "M15 5A3 3 0 1 0 21 5A3 3 0 1 0 15 5Z" +
        "M3 12A3 3 0 1 0 9 12A3 3 0 1 0 3 12Z" +
        "M15 19A3 3 0 1 0 21 19A3 3 0 1 0 15 19Z" +
        "M5.6 11.3L6.4 12.7L18.4 5.7L17.6 4.3Z" +
        "M6.4 11.3L5.6 12.7L17.6 19.7L18.4 18.3Z"

/**
 * Asking about a phrase, over the feed as the cap's page is: the prompt written out for it, to be edited, and the button
 * that hands it to the system's menu, where the person picks ChatGPT, a messenger or anything else. Where there is no
 * such menu the button copies it. What is edited stays until the page is left.
 */
@Composable
internal fun AskPage(
    entry: FeedPage.Phrase,
    prompt: String,
    strings: LearnStrings,
    viaMenu: Boolean,
    onShare: (String) -> Boolean,
    onBack: () -> Unit,
) {
    val ask = strings.ask
    var text by remember(prompt) { mutableStateOf(prompt) }
    // What the last press did: null before one, and again once the text is changed.
    var shared by remember(prompt) { mutableStateOf<Boolean?>(null) }
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(verbColor(entry.verb.word))
            // What this page does not take for itself does not go through to the feed under it.
            .pointerInput(Unit) {}
            .testTag("learn:ask-page"),
    ) {
        TopArrow(strings.back, "learn:ask-back", onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // No drag of the mouse here: in the field it selects text.
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        ) {
            Text(text = ask.title, style = AppTheme.title, color = AppTheme.foreground, modifier = Modifier.testTag("learn:ask-title"))
            Text(
                text = entry.sentence.english,
                style = AppTheme.subtitle.copy(fontWeight = AppTheme.body.fontWeight),
                color = AppTheme.foreground,
                modifier = Modifier.padding(top = 8.dp).testTag("learn:ask-sentence"),
            )
            Text(
                text = ask.about,
                style = AppTheme.footnote,
                color = AppTheme.muted,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
            AppTextField(
                value = text,
                onValueChange = {
                    text = it
                    shared = null
                },
                label = ask.field,
                singleLine = false,
                minHeight = 160.dp,
                modifier = Modifier.testTag("learn:ask-prompt"),
            )
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = if (viaMenu) ask.share else ask.copy,
                onClick = { shared = onShare(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("learn:ask-send"),
            )
            Text(
                text = if (viaMenu) ask.viaMenu else ask.viaCopy,
                style = AppTheme.footnote,
                color = AppTheme.muted,
                modifier = Modifier.padding(top = 8.dp).testTag("learn:ask-how"),
            )
            // The menu shows for itself that it opened; a copy has nothing to show but this.
            when (shared) {
                false -> Notice(ask.failed)
                true -> if (!viaMenu) Notice(ask.copied)
                null -> Unit
            }
            if (text != prompt) {
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = ask.reset,
                    onClick = {
                        text = prompt
                        shared = null
                    },
                    variant = ButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag("learn:ask-reset"),
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = AppTheme.body,
        color = AppTheme.foreground,
        modifier = Modifier.padding(top = 8.dp).testTag("learn:ask-done"),
    )
}

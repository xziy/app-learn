package cx.m42.english.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import cx.m42.superizer.theme.AppTheme
import cx.m42.superizer.ui.components.AppButton
import cx.m42.superizer.ui.components.AppTextField
import cx.m42.superizer.ui.components.ButtonSize
import cx.m42.superizer.ui.components.ButtonVariant
import cx.m42.superizer.ui.platform.SystemBackHandler

/**
 * Adding a book: a link pasted or read from a QR code, and what came of downloading it. The book lands in the app's
 * folder and on the shelf; the screen says where that folder is, since a file put there by hand shows as well.
 */
@Composable
internal fun FetchScreen(instance: BooksInstance, fetch: Fetch, strings: BooksStrings, colors: PaperColors) {
    SystemBackHandler(enabled = true) { instance.closeFetch() }
    val working = fetch.state == Fetching.Working
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = ArrowRoom)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                .testTag("books:fetch"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = PageWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = strings.fetchTitle, style = AppTheme.title, color = colors.text)
                Text(text = strings.fetchHint, style = AppTheme.body, color = colors.muted)
                AppTextField(
                    value = fetch.link,
                    onValueChange = instance::editLink,
                    placeholder = strings.linkPlaceholder,
                    label = strings.linkLabel,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.testTag("books:link"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(
                        text = if (working) strings.downloading else strings.download,
                        onClick = instance::download,
                        enabled = !working && fetch.link.isNotBlank(),
                        size = ButtonSize.Default,
                        modifier = Modifier.testTag("books:download"),
                    )
                    if (instance.canScan) {
                        AppButton(
                            text = strings.scan,
                            onClick = instance::scan,
                            enabled = !working,
                            variant = ButtonVariant.Outline,
                            size = ButtonSize.Default,
                            modifier = Modifier.testTag("books:scan"),
                        )
                    }
                }
                when (val state = fetch.state) {
                    Fetching.Idle, Fetching.Working -> Unit
                    is Fetching.Done -> {
                        Text(
                            text = strings.outcome(state.outcome, state.book.title),
                            style = AppTheme.body,
                            color = colors.text,
                            modifier = Modifier.testTag("books:fetch-done"),
                        )
                        AppButton(
                            text = strings.readNow,
                            onClick = { instance.readFetched(state.book.slug) },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Default,
                            modifier = Modifier.testTag("books:fetch-read"),
                        )
                    }
                    is Fetching.Failed -> Text(
                        text = strings.problem(state.problem, state.status),
                        style = AppTheme.body,
                        color = AppTheme.danger,
                        modifier = Modifier.testTag("books:fetch-failed"),
                    )
                }
                instance.folderLocation?.let { location ->
                    Text(
                        text = strings.folder(location, instance.folderInFiles),
                        style = AppTheme.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 10.dp).testTag("books:folder"),
                    )
                }
            }
        }
        TopArrow(strings.back, "books:fetch-back", instance::closeFetch)
    }
}

package cx.m42.english

import androidx.compose.runtime.Composable
import cx.m42.english.books.BooksApp
import cx.m42.english.learn.LearnApp
import cx.m42.superizer.LanguageOption
import cx.m42.superizer.Superizer
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.host.SuperizerBuilder
import cx.m42.superizer.host.build
import cx.m42.superizer.host.platform.currentPlatform
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.ui.shell.SuperizerShell
import kotlinx.coroutines.CoroutineScope

/** `english://app/<id>/<path>` and `english://activate?…` — on the desktop, `--link=`. */
public const val SCHEME: String = "english"

/**
 * The host: one Superizer, one context, and the apps of this repository. Adding an app is one more
 * `register` line; everything the person sees around it — Home, the drawer, Settings — is the library's.
 */
public fun buildEnglishHost(
    scope: CoroutineScope,
    debug: Boolean,
    extra: SuperizerBuilder.() -> Unit = {},
): Superizer = Superizer.build(scope) {
    host(
        HostInfo(
            name = "English",
            version = BuildInfo.VERSION,
            build = BuildInfo.COMMIT + if (BuildInfo.DIRTY) "*" else "",
            platform = currentPlatform(),
            contractVersion = SuperizerContract.VERSION,
            debug = debug,
        ),
    )
    scheme(SCHEME)
    languages(
        LanguageOption("ru", "Русский"),
        LanguageOption("en", "English"),
        fallback = "ru",
    )
    register(LearnApp())
    register(BooksApp())
    // What a fresh install shows; after that Home is the person's, and a new app is added to it from All Apps.
    home("learn", "books")
    extra()
}

/** The root composable every entry point calls. */
@Composable
public fun English(superizer: Superizer) {
    SuperizerShell(superizer)
}

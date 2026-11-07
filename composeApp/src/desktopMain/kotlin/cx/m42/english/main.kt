package cx.m42.english

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cx.m42.superizer.host.storage.PrefsStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The app as a desktop window. `--link=` and `--activate=` stand in for a QR code and a notification tap. */
fun main(args: Array<String>) {
    // A directory of its own, so no other Superizer host on this computer shares its Home or language.
    PrefsStorage.useDirectory(File(System.getProperty("user.home"), ".english"))
    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val superizer = remember { buildEnglishHost(scope, debug = true) }

        remember {
            args.firstOrNull { it.startsWith("--link=") || it.startsWith("--activate=") }
                ?.substringAfter('=')
                ?.let { superizer.route.deliver(it) }
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 420.dp, height = 820.dp),
            title = "English",
        ) {
            English(superizer)
        }
    }
}

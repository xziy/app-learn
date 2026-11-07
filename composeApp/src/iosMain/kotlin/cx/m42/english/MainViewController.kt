package cx.m42.english

import androidx.compose.ui.window.ComposeUIViewController
import cx.m42.superizer.Superizer
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.UIKit.UIViewController

/**
 * One host per process, not per controller: SwiftUI may make a second controller on a scene reconnect,
 * and it draws the same host.
 */
@OptIn(ExperimentalNativeApi::class)
private val superizer: Superizer by lazy {
    buildEnglishHost(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        debug = Platform.isDebugBinary,
    )
}

/** The whole app as one UIKit controller, which `iosApp/ContentView.swift` wraps for SwiftUI. */
fun MainViewController(): UIViewController = ComposeUIViewController { English(superizer) }

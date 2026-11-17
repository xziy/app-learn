package cx.m42.english.learn

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal actual fun platformSharing(): Sharing = ClipboardSharing

/**
 * A desktop's share menu, where it has one (macOS), is not reachable from Java, and Linux has none at all: the text goes
 * to the clipboard instead, to be pasted into ChatGPT or a messenger by hand.
 */
private object ClipboardSharing : Sharing {
    override val viaMenu: Boolean = false

    override fun share(text: String): Boolean =
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        } catch (e: Exception) {
            // Headless, or the clipboard held by another program for the moment.
            false
        }
}

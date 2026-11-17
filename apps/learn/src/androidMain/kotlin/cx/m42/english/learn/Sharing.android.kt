package cx.m42.english.learn

import android.content.Context
import android.content.Intent

internal actual fun platformSharing(): Sharing = AndroidSharing { LearnContextProvider.app }

/**
 * The system's chooser over `ACTION_SEND` with plain text: ChatGPT, the messengers, mail and notes each take it. The
 * module has only the application's Context ([LearnContextProvider]), so the chooser starts in a task of its own.
 */
private class AndroidSharing(private val context: () -> Context?) : Sharing {
    override val viaMenu: Boolean = true

    override fun share(text: String): Boolean {
        val context = context() ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            // No activity to take it, or the system refused to start one.
            false
        }
    }
}

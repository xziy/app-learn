package cx.m42.english.learn

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

internal actual fun platformSharing(): Sharing = IosSharing

/**
 * The system's share sheet, `UIActivityViewController`, over whatever is on screen: ChatGPT, the messengers, notes and
 * copying are all in it. The module has no view controller of its own, so it presents from the top of the key window.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosSharing : Sharing {
    override val viaMenu: Boolean = true

    override fun share(text: String): Boolean {
        val top = topController() ?: return false
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // On an iPad the sheet is a popover and needs something to point at: the middle of the screen, without an arrow.
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = top.view
            top.view.bounds.useContents { popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            popover.permittedArrowDirections = 0uL
        }
        top.presentViewController(sheet, animated = true, completion = null)
        return true
    }

    private fun topController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }
        var top = window?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }
}

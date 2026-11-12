package cx.m42.english.books

import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime
import kotlinx.serialization.Serializable

/** A closed book with a bookmark cut out of its cover, as an SVG path over a 24×24 viewBox. */
private const val BOOK =
    "M18 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V4C20 2.9 19.1 2 18 2Z" +
        "M9 4H13V11L11 9.5L9 11V4Z"

/**
 * What the reader opens with. [download] is the address of a book archive: the download screen opens with it filled
 * in, from a QR code (`<scheme>://activate?a=books&c=…`) or a link (`<scheme>://app/books/download?url=…`). It never
 * downloads by itself — the person sees the address and says so.
 */
@Serializable
public data class BooksConfig(val download: String? = null)

/** Reading books the English way. The host knows nothing else about this module than this class and its config. */
public class BooksApp internal constructor(private val source: BookSource) : SuperizerApp<BooksConfig>() {

    public constructor() : this(ResourceBookSource)

    override val manifest: AppManifest = AppManifest(
        id = AppId("books"),
        version = "0.1.0",
        metadata = AppMetadata(
            title = localized("ru" to "Книги", "en" to "Books"),
            description = localized(
                "ru" to "Книги по-русски, как думает англичанин и по-английски",
                "en" to "Books in Russian, the English way of thinking and English",
            ),
            icon = AppIcon.Path(BOOK),
        ),
        deepLinks = setOf(DOWNLOAD_PATH),
    )

    override val configSpec: AppConfigSpec<BooksConfig> = AppConfigSpec(BooksConfig.serializer(), BooksConfig())

    override fun setup(ctx: AppSetupContext) {
        ctx.deepLink(DOWNLOAD_PATH) { params -> configSpec.encode(BooksConfig(download = params["url"])) }
    }

    override fun launch(runtime: InstanceRuntime, config: BooksConfig): AppInstance =
        BooksInstance(runtime, source, config.download)
}

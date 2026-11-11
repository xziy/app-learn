package cx.m42.english.books

import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime

/** A closed book with a bookmark cut out of its cover, as an SVG path over a 24×24 viewBox. */
private const val BOOK =
    "M18 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V4C20 2.9 19.1 2 18 2Z" +
        "M9 4H13V11L11 9.5L9 11V4Z"

/** Reading books the English way. The only public class of this module; the host knows nothing else about it. */
public class BooksApp internal constructor(private val source: BookSource) : SuperizerApp<Unit>() {

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
    )

    override val configSpec: AppConfigSpec<Unit> = AppConfigSpec.None

    override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance = BooksInstance(runtime, source)
}

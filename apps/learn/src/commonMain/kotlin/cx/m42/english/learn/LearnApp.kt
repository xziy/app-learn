package cx.m42.english.learn

import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppIcon
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.runtime.InstanceRuntime

/** An open book, as an SVG path over a 24×24 viewBox. `AppIcon.Letter` draws only an «A» in its ring. */
private const val BOOK =
    "M11 6.5C9.2 5.3 6.8 4.7 3 4.7V18.5C6.8 18.5 9.2 19.1 11 20.3V6.5Z" +
        "M13 6.5C14.8 5.3 17.2 4.7 21 4.7V18.5C17.2 18.5 14.8 19.1 13 20.3V6.5Z"

/** Learning English. The only public class of this module; the host knows nothing else about it. */
public class LearnApp internal constructor(
    private val source: VerbSource,
    private val speech: Speech,
    /** How verbs are put in random order: tests want to know which ones come. */
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() },
) : SuperizerApp<Unit>() {

    public constructor() : this(ResourceVerbSource, platformSpeech())

    override val manifest: AppManifest = AppManifest(
        id = AppId("learn"),
        version = "0.2.0",
        metadata = AppMetadata(
            title = localized("ru" to "Английский", "en" to "English"),
            icon = AppIcon.Path(BOOK),
        ),
    )

    override val configSpec: AppConfigSpec<Unit> = AppConfigSpec.None

    override fun launch(runtime: InstanceRuntime, config: Unit): AppInstance =
        LearnInstance(runtime, source, speech, shuffle)
}

# English

A Kotlin Multiplatform / Compose Multiplatform host for learning English — `cx.m42.english`. Built on
[Superizer](https://github.com/adminization/superizer) the same way as
[restoapp-touch](https://github.com/webresto/restoapp-touch), without its servers: one host, one
context, and the apps in `apps/<name>`. The decisions are in [`.ai-notes`](.ai-notes/README.md).

## Requirements

| | |
|---|---|
| JDK | 17 |
| Gradle | none — use the bundled `./gradlew` |
| Superizer | its sources at `../superizer` (`git clone git@github.com:adminization/superizer.git ../superizer`); `SUPERIZER_FROM_MAVEN=1` builds against mavenLocal instead |
| Speech on a desktop | `say` on macOS; `espeak-ng`, `espeak` or `spd-say` on Linux (`sudo dnf install espeak-ng`). Without one the card says so. iOS has its own. |

## Running it

```bash
./gradlew :composeApp:run              # a 420×820 window
./gradlew desktopTest                  # every module's tests
./gradlew :composeApp:createDistributable   # a folder with its own launcher and JRE
```

`desktopTest` also leaves the main states as PNGs in `composeApp/build/screenshots`.

The distribution lands in `composeApp/build/compose/binaries/main/app/English` and starts with
`bin/English`; nothing has to be installed. Its JRE is cut from the JDK Gradle runs on, so build with a
portable one such as Temurin if it is to run on another Linux. It holds only the Java modules listed in
`composeApp/build.gradle.kts`: after adding a dependency, check them with
`./gradlew :composeApp:suggestRuntimeModules`.

## The feed

`apps/learn` opens on the **list of chosen verbs**: a round of five (`WordsAtOnce` in `Feed.kt`) drawn at random
from the verbs of the theme not seen yet. The round stays until it is gone through, and then the next one is
drawn; **Pick 5 at random** draws another at once. Below are the rest of the theme, to add by hand. A row opens
the feed at that verb, the button above goes on from where the person stopped.

**The seen verbs** have a page of their own, the latest on top. A verb gets there when the feed goes forward out
of it, past its last card; a verb left halfway stays chosen. A row opens the feed of that verb alone, and that
changes neither the marks nor the place in the round. A swipe to either side takes the mark off, the button at
the bottom takes all of them off with a second press. A verb with its mark off can be drawn again.

**The theme** is chosen at the top of that list: each has its own verbs and phrases, and keeps its own choice,
its own seen verbs and its own place. With only one theme there is nothing to choose, and the choice is not shown.

The feed goes through the chosen verbs one after another, full screen, one card at a time, as in Reels.
Each verb goes through all its tenses, and everything of one verb has that verb's own muted dark colour.

- **A verb's card** comes first: how it is said, its meanings, its forms and what is particular about it.
- **A tense's cover** comes before its phrases and says, in Russian and in English at once, which tense this
  is, what it is for and which portion of cards comes next: `Порция 3 из 14 · 2 карточки`. A portion is the
  phrases of one tense. Where a verb has no such tense, the cover says why, there are no phrases, and it is not
  counted as a portion. That reason is in the base in Russian only.
- **A phrase** goes left and right: how we think, how the English think, the English with its transcription.
  That last step is said aloud as it comes up, and the speaker on it says it again. With nothing to say it
  with, the step says so.
- **Up and down** is the next or the previous card.
- **A tap or a click** goes forward: along the phrase, and from its last step to the next card.
- **Play** goes through all of it by itself: four seconds a step (`StepSeconds` in `Feed.kt`), the last step
  has the time of its sound on top, and a card to read has eight (`IntroSeconds`). It stops at the end.
- On a desktop: also drag with the mouse, the wheel, or the arrow keys. The place is kept across restarts.
- **What does not fit scrolls**, every card and step and both lists, by a finger, the wheel and a mouse's drag; a card
  by the arrow keys too. A card is read to its end first, and only a new swipe, a new drag or the wheel after a pause
  leaves it. A tap still goes forward at once.
- **The word at the bottom** of a phrase has its transcription, and the letter **i** next to it opens what
  is on the verb's card again, with what the transcription signs mean.

## Where the verbs come from

The app reads only its own copy of the base. It never reads `../verbs` while it runs.

| | |
|---|---|
| The copy | `apps/learn/src/commonMain/composeResources/files/verbs/` |
| `themes.json` | the themes that have verbs: name → description |
| `<theme>/index.json` | the theme's verbs in the order they are learned, because resources cannot be listed at run time |
| `<theme>/<verb>.json` | one verb, as the skill wrote it |

`ResourceVerbSource` in `Verbs.kt` reads these files with `Res.readBytes("files/verbs/…")`. They are packed into the
build, so a built app, a distribution included, has only the words it was built with.

The source is `../verbs`, that is `/prj/english/verbs`. It holds `<theme>/<verb>/<verb>.json`, written by the
dragunkin-verbs skill, the themes in `themes.json` and each theme's journal in `<theme>/progress.json`. The name of a
theme is what stands before the colon of its description there.

The copy is made by hand: `syncVerbs` is not part of the build, and `run` or `createDistributable` do not call it.
After new verbs or themes appear in `../verbs`:

```bash
./gradlew :apps:learn:syncVerbs     # refresh the copy
./gradlew :composeApp:run           # or createDistributable: only a new build has the new words
```

`syncVerbs` (in `apps/learn/build.gradle.kts`) deletes the copy and makes it again, so a verb removed from `../verbs`
disappears from the app too. It puts a theme's verbs in the order of the theme's journal; verbs that are not in the
journal come after them, by name. A theme without verbs is left out, and so is `nouns.json`. The copy is kept in the
repository so that CI, which has no `../verbs`, still builds with the words.

## iPhone

`iosApp/` is the Xcode project, `composeApp/src/iosMain` the host on iOS. Linking needs a Mac with
Xcode; only the klibs compile without it (`./gradlew :composeApp:compileKotlinIosArm64`).
`.github/workflows/ios.yml` builds on `main` and by hand, and leaves an **unsigned**
`English-<run>.ipa` in the run's artifacts. A phone installs it only after it is signed.

## How it is put together

```
composeApp/src/
  commonMain/EnglishHost.kt      the host: the apps it registers, its languages and scheme
  desktopMain/main.kt            the window; settings in ~/.english
  iosMain/MainViewController.kt  the host as one UIKit controller
apps/learn/src/
  commonMain/kotlin/…/learn/
    LearnApp.kt                  the only public class: the manifest
    LearnInstance.kt             the state: loads a session, draws a round, keeps the place and the seen verbs
    LearnScreen.kt               the feed: pagers, cards, gestures, play
    VerbList.kt                  the theme and the list of chosen verbs
    SeenList.kt                  the page of the seen verbs: going back to one, swiping the mark off
    Feed.kt, Verbs.kt            pure logic: which pages, a verb's colour, reading a verb's JSON
    Speech.kt                    speech; Speech.desktop.kt and Speech.ios.kt do the talking
  commonMain/composeResources/files/verbs/   the copy of the base: themes.json, <theme>/index.json, <theme>/<verb>.json
iosApp/                          the Xcode project
```

A new app is a module in `apps/<name>`, an `include` in `settings.gradle.kts`, a dependency in
`composeApp/build.gradle.kts` and one `register(…)` line in `EnglishHost.kt`. An app sees only its
`AppRuntime` — never `superizer:host`, the host module or another app. How to write one:
`../superizer/docs/03-app-guide.md`.

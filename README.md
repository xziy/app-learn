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
| Speech on a desktop | `say` on macOS; `espeak-ng`, `espeak` or `spd-say` on Linux (`sudo dnf install espeak-ng`). Without one the card says so. iOS and Android have their own. |
| Android SDK | compileSdk 36, minSdk 24, in `ANDROID_HOME` or `local.properties`. Superizer's modules ask for it too. |

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
- **Up and down** is the next or the previous card. A phrase the feed comes back to starts again from its first step,
  how we say it; only the stored place opens on the step it was left on.
- **A tap or a click** goes forward: along the phrase, and from its last step to the next card.
- **Play** goes through all of it by itself: four seconds a step (`StepSeconds` in `Feed.kt`), the last step
  has the time of its sound on top, and a card to read has eight (`IntroSeconds`). It stops at the end.
- On a desktop: also drag with the mouse, the wheel, or the arrow keys. The place is kept across restarts.
- **What does not fit scrolls**, every card and step and both lists, by a finger, the wheel and a mouse's drag; a card
  by the arrow keys too. A card is read to its end first, and only a new swipe, a new drag or the wheel after a pause
  leaves it. A tap still goes forward at once.
- **The word at the bottom** of a phrase has its transcription, and the letter **i** next to it opens what
  is on the verb's card again, with what the transcription signs mean.
- **The form** is a quiet line at the top of a phrase, between the arrows: which tense and which kind of sentence the
  phrase is, `Тип 1 · Прошедшее · Вопрос`, and `Актив · Past Simple` dimmer after it. The kinds are a statement, a negative, a question and, in the future in the past, a
  reported question (`He asked whether…`). The **cap** before it, the whole line in fact, opens a page about that form:
  its formula in English and in the thought (`Did + кто + V + …?`, `[Сделал] + кто + мыть + …?`), what `V`, `V2` and the
  rest are for this verb, the phrase itself in the colours of the formula with each part named, how the form is built,
  and the formulas of the other kinds of the tense. The base says none of this: `Grammar.kt` reads it off the English.
  The verb is the last word that is one of its forms, its auxiliaries, `not` and adverbs are the words straight before it,
  a question has the first auxiliary in front, and a statement has its subject before the group, back to its determiner.
  Over the whole base every phrase has its verb found (`GrammarTest`).
- **Speech** is a little slower than the platform's own pace (`SpeechPace` in `Speech.kt`), and the phrases take turns
  among the voices there are, with three pitches on top (`timbreFor`): one phrase always has the same voice, most of its
  neighbours another, so that they sound like different people. espeak takes turns among its own variants (`en-us+m3`,
  `+f2`…), spd-say among its kinds of voice, `say` among the macOS voices installed; Android among the engine's voices of
  the chosen English that need no network, iOS among Apple's own American voices.

## The words

**A tap or a click on a noun** of the English phrase, or on its word in the transcription, opens **the word's page** over the
feed, which stays where it was: how the word is said, its plural, its senses with the one of the phrase first, synonyms, what
it goes with, words of its root, notes, and every phrase it stands in. A phrase there opens its verb's feed on it. A tap
anywhere else still goes forward. The nouns not marked as known are underlined.

A noun is **seen** once the English of a phrase with it is on screen. **The dictionary**, next to the seen verbs in the list,
has the words seen, the latest on top, to search by the word, a form or a translation, and to mark **I know it**, a sense at a
time: `sheet` as a rope is another mark than `sheet` as paper. The marks are in `runtime.storage`, `lex.seen` and `lex.known`,
under `n:<word>` or `n:<word>#<sense>`, and are the same in every theme.

## The books

`apps/books` is a reader of books put the English way by the dragunkin-text skill. It opens on **the shelf**, the books read
lately on top, each with how much of it is translated and how much is read. A book opens **full screen** as one long page.

- **The lines** are chosen in the sheet behind **Aa**: how we think, how the English think, how it is said, the transcription.
  With several lines each sentence has them one under another; with one the paragraph runs on as in any book.
- **A tap on a sentence** opens its lines that are off under it, a second tap closes them.
- **The nouns** of a sentence have a colour each, the same in every line: `name` in the English, «имя» in the thought, «имени» in
  the Russian. A tap on one opens its card: how it is said, what it means here, how it stands in each line, its other senses,
  its plural and how often the book has it.
- **The page** is light, warm or dark, the type larger or smaller, with serifs or without. The bars go away while reading down
  and come back going up. The place in each book is kept, and the settings are the same for all books.
- On a desktop: the arrows, Page Up and Down, Space, Home and End scroll, `+` and `-` change the size, Esc goes back.

A book is `../texts/<name>/book.json`, written by the skill's `render_text.py` next to its md; the format is in the skill's SKILL.md,
«Книга для читалки». The app carries copies, as with the verbs:

```bash
./gradlew :apps:books:syncBooks     # ../texts/*/book.json into files/books, with files/books/index.json
```

The copies stay out of git (`.gitignore`): the repository is public and some texts are under copyright. A build from a
clean checkout has an empty shelf; run `syncBooks` on the machine that builds to carry books.

### Adding a book to an installed app

A book also travels on its own, as `<name>.book.tgz` — a tar in gzip with `meta.json` and `book.json` — which the skill's
`pack_book.py` writes (its SKILL.md, «Архив для загрузки»):

```bash
cd ../.claude/skills/dragunkin-text
python3 scripts/pack_book.py phosphorus --url https://example.org/phosphorus.book.tgz --scheme english --qr
```

Put the archive at that address, then on the shelf tap **+**: paste the link, or read its QR code where the host has a
camera, and **Download**. The book lands in the app's folder and on the shelf, and reads without the network from then on.
The folder is Superizer's `AppFolder` (0.8.2), one the person sees: `~/Documents/English/books` on a desktop, Files › On My
iPhone › the host › `books` on iOS. An archive copied in by hand shows on the shelf as well. A downloaded book stands in for
a carried one with the same name, and can be deleted from the shelf.

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

The dictionary comes the same way, from `../lexicon/nouns/<word>.json` of the lexicon skill: `syncLexicon` puts it in one file,
`files/lexicon/nouns.json`, with the verbs each word stands in, its forms there and how many phrases each sense has.

The copy is made by hand: `syncVerbs` and `syncLexicon` are not part of the build, and `run` or `createDistributable` do not
call them. After new verbs, themes or words appear in `../verbs` or `../lexicon`:

```bash
./gradlew :apps:learn:syncVerbs :apps:learn:syncLexicon     # refresh the copies
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

## Android

Only `apps/learn` has an Android target, for the hosts that have Android (Unitool); this repository's host
has none, so there is no APK here. `./gradlew :apps:learn:assembleRelease` builds the module as an AAR.

Speech there is the phone's `TextToSpeech`. The module brings two lines into the host's manifest
(`apps/learn/src/androidMain/AndroidManifest.xml`): the query for speech engines that Android 11+ asks for,
and a provider that hands the module the application's Context, since an app does not see the host's.

There is no web target: speech has no web side, so a host with one leaves this app out of its web build.

## How it is put together

```
composeApp/src/
  commonMain/EnglishHost.kt      the host: the apps it registers, its languages and scheme
  desktopMain/main.kt            the window; settings in ~/.english
  iosMain/MainViewController.kt  the host as one UIKit controller
apps/learn/src/
  commonMain/kotlin/…/learn/
    LearnApp.kt                  the only public class: the manifest
    LearnInstance.kt             the state: loads a session, draws a round, keeps the place, the seen verbs and the words
    LearnScreen.kt               the feed: pagers, cards, gestures, play
    VerbList.kt                  the theme and the list of chosen verbs
    SeenList.kt                  the page of the seen verbs: going back to one, swiping the mark off
    Feed.kt, Verbs.kt            pure logic: which pages, a verb's colour, reading a verb's JSON
    Grammar.kt                   pure logic: the kind of a phrase, the formulas of each tense, a phrase laid along them
    GrammarPage.kt, GrammarStrings.kt   the form above the word, the cap's page and its words
    Lexicon.kt, PhraseLinks.kt   pure logic: the dictionary, its search, where a noun stands in a phrase, its links
    WordPage.kt, Dictionary.kt, WordStrings.kt   a word's page, the dictionary and their words
    Speech.kt                    speech, its pace and whose voice says a phrase; Speech.desktop.kt, Speech.ios.kt and
                                 Speech.android.kt do the talking
apps/books/src/commonMain/kotlin/…/books/
    BooksApp.kt                  the only public class: the manifest
    BooksInstance.kt             the state: the shelf, the open book, the place in each book, the settings
    Books.kt                     pure logic: a book, its sentences and nouns, reading book.json and the shelf's index
    Marks.kt                     pure logic: where each noun stands in each line, and in the transcription
    BooksScreen.kt, Reader.kt    the shelf and the reader
    Lines.kt                     a line or a paragraph drawn with its nouns, and where a tap lands
    Sheets.kt                    the sheets over the page: how to read, the contents, a noun's card
    Paper.kt, BooksStrings.kt    the pages' colours and the app's words
  commonMain/composeResources/files/books/   the copy of the books: index.json, <name>.json
  commonMain/composeResources/files/verbs/   the copy of the base: themes.json, <theme>/index.json, <theme>/<verb>.json
  commonMain/composeResources/files/lexicon/nouns.json   the copy of the dictionary, with where its words stand
iosApp/                          the Xcode project
```

A new app is a module in `apps/<name>`, an `include` in `settings.gradle.kts`, a dependency in
`composeApp/build.gradle.kts` and one `register(…)` line in `EnglishHost.kt`. An app sees only its
`AppRuntime` — never `superizer:host`, the host module or another app. How to write one:
`../superizer/docs/03-app-guide.md`.

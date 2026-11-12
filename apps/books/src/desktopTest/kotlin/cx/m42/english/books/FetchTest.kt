package cx.m42.english.books

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextReplacement
import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.FileDownloader
import cx.m42.superizer.runtime.QrScanner
import cx.m42.superizer.testing.AppTestScope
import cx.m42.superizer.testing.FakeAppFolder
import cx.m42.superizer.testing.FakeDownloader
import cx.m42.superizer.testing.FakeQrScanner
import cx.m42.superizer.testing.runAppTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A build that carries no books, as one from a clean checkout: its shelf is the folder alone. */
private object NoBooks : BookSource {
    override suspend fun index(): List<BookInfo> = error("no files/books/index.json")

    override suspend fun read(slug: String): String = error("no $slug")
}

/** A build that carries one book, `cat`, which a download of `cat` stands in for. */
private object OneBook : BookSource {
    override suspend fun index() = listOf(BookInfo("cat", "Кот из сборки", paragraphs = 1, sentences = 1, translated = 1))

    override suspend fun read(slug: String): String = bookJson("Кот из сборки", paragraphs = 1)
}

private const val URL = "https://books.example/dogs.book.tgz"

@OptIn(ExperimentalTestApi::class)
class FetchTest {

    private val folder = FakeAppFolder(location = "Unitool/books")
    private val downloader = FakeDownloader()
    private val scanner = FakeQrScanner()

    private fun run(
        source: BookSource = NoBooks,
        config: BooksConfig = BooksConfig(),
        services: Boolean = true,
        camera: Boolean = false,
        block: AppTestScope<BooksConfig>.() -> Unit,
    ) {
        val app = BooksApp(source)
        runAppTest(app, config = app.configSpec.encode(config), prepare = {
            if (services) {
                it.provide(AppFolder.Key, folder)
                it.provide(FileDownloader.Key, downloader)
            }
            if (camera) it.provide(QrScanner.Key, scanner)
        }, block = block)
    }

    private fun AppTestScope<BooksConfig>.fetch(link: String) {
        node("books:link").performTextReplacement(link)
        idle()
        click("books:download")
        idle()
    }

    // ------------------------------------------------------------ links

    @Test
    fun aLinkIsTheArchivesAddressInAnyOfItsThreeShapes() {
        assertEquals(URL, bookLink("  $URL \n"))
        assertEquals("http://books.example/a.book.tgz", bookLink("http://books.example/a.book.tgz"))
        val encoded = java.net.URLEncoder.encode(URL, "UTF-8")
        assertEquals(URL, bookLink("unitool://app/books/download?url=$encoded"))
        val config = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"download":"$URL"}""".encodeToByteArray())
        assertEquals(URL, bookLink("english://activate?a=books&c=$config"))
        assertEquals(URL, bookLink("unitool://activate?appId=books&config=" + java.net.URLEncoder.encode("""{"download":"$URL"}""", "UTF-8")))
    }

    @Test
    fun anythingElseIsNoLink() {
        val config = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"download":"$URL"}""".encodeToByteArray())
        val file = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"download":"file:///etc/passwd"}""".encodeToByteArray())
        for (text in listOf(
            "", "   ", "books.example/dogs.book.tgz", "https://", "ftp://books.example/a.tgz", "file:///etc/passwd",
            "javascript:alert(1)", "https://books.example/a b.tgz", "unitool://activate?a=learn&c=$config",
            "unitool://activate?a=books&c=$file", "unitool://app/books/download?url=file%3A%2F%2F%2Fetc",
            "unitool://app/learn/download?url=${java.net.URLEncoder.encode(URL, "UTF-8")}",
        )) {
            assertNull(bookLink(text), text)
        }
    }

    // ------------------------------------------------------------ the shelf

    @Test
    fun anArchiveInTheFolderIsOnTheShelfAndOpens() {
        folder.put("dogs.book.tgz", archive("dogs", "Собаки"))
        run {
            assertShown("books:book-dogs")
            node("books:book-dogs").assertTextContains("Собаки")
            node("books:book-dogs").assertTextContains("Скачана")
            click("books:book-dogs")
            assertShown("books:reader")
        }
    }

    @Test
    fun aDownloadedBookStandsInForTheOneTheBuildCarries() {
        folder.put("cat.book.tgz", archive("cat", "Кот скачанный"))
        run(source = OneBook) {
            node("books:book-cat").assertTextContains("Кот скачанный")
            assertAbsent("books:book-cat-2")
        }
    }

    @Test
    fun aFileThatIsNotABookIsLeftOffAndTheRestShows() {
        folder.put("broken.book.tgz", "<html>".encodeToByteArray())
        folder.put("notes.txt", "hello".encodeToByteArray())
        folder.put("dogs.book.tgz", archive("dogs", "Собаки"))
        run {
            assertShown("books:book-dogs")
            assertAbsent("books:book-broken")
            assertTrue(runtime.logger.lines.any { "broken.book.tgz" in it })
        }
    }

    @Test
    fun anEmptyShelfOffersToAddABookWhereTheHostCanKeepOne() {
        run {
            assertShown("books:empty")
            assertShown("books:add")
        }
        run(services = false) {
            assertShown("books:empty")
            assertAbsent("books:add")
        }
    }

    // ------------------------------------------------------------ downloading

    @Test
    fun aBookDownloadedByItsLinkLandsInTheFolderAndOnTheShelf() {
        val bytes = archive("dogs", "Собаки")
        downloader.respond(URL, bytes)
        run {
            click("books:add")
            assertShown("books:fetch")
            // The fake host is a desktop: the folder is said as its path.
            node("books:folder").assertTextContains("Unitool/books", substring = true)
            fetch(URL)
            node("books:fetch-done").assertTextContains("«Собаки» на полке")
            assertContentEquals(bytes, folder.files["dogs.book.tgz"])
            assertEquals(listOf(URL to MAX_ARCHIVE_BYTES), downloader.calls)
            click("books:fetch-read")
            assertShown("books:reader")
            click("books:back")
            assertShown("books:book-dogs")
        }
    }

    @Test
    fun theSameBookAgainIsNotWrittenAndANewVersionReplacesTheOld() {
        val first = archive("dogs", "Собаки")
        folder.put("my copy.book.tgz", first)
        downloader.respond(URL, first)
        val second = archive("dogs", "Собаки", book = bookJson("Собаки", paragraphs = 5))
        downloader.respond("https://books.example/v2.book.tgz", second)
        run {
            click("books:add")
            fetch(URL)
            node("books:fetch-done").assertTextContains("«Собаки» уже на полке, эта же версия")
            assertEquals(setOf("my copy.book.tgz"), folder.files.keys)

            fetch("https://books.example/v2.book.tgz")
            node("books:fetch-done").assertTextContains("«Собаки» обновлена")
            // One book, one file: the person's own copy under another name goes.
            assertEquals(setOf("dogs.book.tgz"), folder.files.keys)
            assertContentEquals(second, folder.files["dogs.book.tgz"])
        }
    }

    @Test
    fun eachFailureSaysWhatWentWrongAndWritesNothing() {
        downloader.fail("https://books.example/missing", status = 404)
        downloader.fail("https://books.example/offline")
        downloader.respond("https://books.example/page", "<html>Hello</html>".encodeToByteArray())
        downloader.respond(
            "https://books.example/newer",
            archive("future", metaOverride = """{"format":"english-book","version":9,"id":"future"}"""),
        )
        val cases = listOf(
            "not a link" to "Это не ссылка на книгу",
            "https://books.example/missing" to "сервер ответил 404",
            "https://books.example/offline" to "нет ответа",
            "https://books.example/page" to "не книга",
            "https://books.example/newer" to "Обновите приложение",
        )
        run {
            click("books:add")
            for ((link, words) in cases) {
                fetch(link)
                node("books:fetch-failed").assertTextContains(words, substring = true)
            }
            assertTrue(folder.files.isEmpty())
        }
    }

    @Test
    fun aFolderThatWillNotTakeTheBookSaysSo() {
        downloader.respond(URL, archive("dogs"))
        folder.failWrites = true
        run {
            click("books:add")
            fetch(URL)
            node("books:fetch-failed").assertTextContains("Не удалось сохранить", substring = true)
        }
    }

    @Test
    fun aQrCodeReadByTheCameraDownloadsAtOnce() {
        val config = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"download":"$URL"}""".encodeToByteArray())
        scanner.next = "unitool://activate?a=books&c=$config"
        downloader.respond(URL, archive("dogs", "Собаки"))
        run(camera = true) {
            click("books:add")
            click("books:scan")
            idle()
            node("books:fetch-done").assertTextContains("«Собаки» на полке")
            assertEquals(1, scanner.scans)
        }
    }

    @Test
    fun noCameraNoScanButton() {
        run {
            click("books:add")
            assertAbsent("books:scan")
        }
    }

    @Test
    fun openedWithABooksAddressTheDownloadScreenWaitsForThePerson() {
        downloader.respond(URL, archive("dogs", "Собаки"))
        run(config = BooksConfig(download = URL)) {
            assertShown("books:fetch")
            assertTrue(downloader.calls.isEmpty())
            click("books:download")
            idle()
            node("books:fetch-done").assertTextContains("«Собаки» на полке")
        }
    }

    @Test
    fun theDeepLinkIsDeclared() {
        assertEquals(setOf("download"), BooksApp().manifest.deepLinks)
    }

    @Test
    fun aDownloadedBookCanBeDeletedAndACarriedOneCannot() {
        folder.put("dogs.book.tgz", archive("dogs", "Собаки"))
        run(source = object : BookSource {
            override suspend fun index() = listOf(BookInfo("bird", "Птица", paragraphs = 1, sentences = 1, translated = 1))
            override suspend fun read(slug: String) = bookJson("Птица", 1)
        }) {
            assertAbsent("books:remove-bird")
            click("books:remove-dogs")
            click("books:remove-keep-dogs")
            assertShown("books:book-dogs")
            click("books:remove-dogs")
            click("books:remove-confirm-dogs")
            idle()
            assertAbsent("books:book-dogs")
            assertTrue(folder.files.isEmpty())
            assertShown("books:book-bird")
        }
    }
}

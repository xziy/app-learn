package cx.m42.english.books

import java.io.ByteArrayInputStream
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The reader's own gzip, DEFLATE and tar, against the JVM's zlib and against the skill's `pack_book.py`. */
class ArchiveTest {

    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResource("/$name")) { name }.readBytes()

    private fun jvmGunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).readBytes()

    @Test
    fun theSkillsOwnArchiveReadsWhole() {
        val archive = resource("cat.book.tgz")
        val meta = readArchiveMeta(archive)
        assertEquals("cat", meta.id)
        assertEquals("Кот", meta.title)
        assertEquals("Тест", meta.author)
        assertEquals(2, meta.paragraphs)
        assertEquals(2, meta.sentences)
        assertEquals("74460753b566e771577f70499d6065bc65453348cb0f05e63587e732c7bed022", meta.sha256)
        val (whole, text) = readArchive(archive)
        assertEquals(meta, whole)
        assertEquals(sha256(text.encodeToByteArray()), meta.sha256)
        val book = parseBook(meta.id, text)
        assertEquals(2, book.paragraphs.size)
        assertTrue(book.paragraphs[0].heading)
        assertEquals("The cat sleeps in the sun.", book.paragraphs[1].sentences[0].english)
    }

    @Test
    fun gzipReadsAsTheJvmReadsItAtEveryLevelAndShape() {
        val random = Random(42)
        val shapes = listOf(
            ByteArray(0),
            byteArrayOf(7),
            ByteArray(100_000) { 'a'.code.toByte() },
            ByteArray(70_000) { random.nextInt(256).toByte() },
            ByteArray(200_000) { (it % 251).toByte() },
            bookJson("Длинная", paragraphs = 400).encodeToByteArray(),
            ByteArray(150_000) { if (random.nextInt(10) == 0) random.nextInt(256).toByte() else 'x'.code.toByte() },
        )
        for ((i, data) in shapes.withIndex()) {
            for (level in listOf(Deflater.NO_COMPRESSION, Deflater.BEST_SPEED, Deflater.DEFAULT_COMPRESSION, Deflater.BEST_COMPRESSION)) {
                val packed = gzip(data, level)
                assertContentEquals(jvmGunzip(packed), gunzip(packed, limit = MAX_BOOK_BYTES), "shape $i at level $level")
            }
            for (strategy in listOf(Deflater.HUFFMAN_ONLY, Deflater.FILTERED)) {
                assertContentEquals(data, gunzip(gzipWithFields(data, strategy = strategy), limit = MAX_BOOK_BYTES), "shape $i, strategy $strategy")
            }
        }
    }

    @Test
    fun theHeadersOptionalFieldsArePassedOver() {
        val data = bookJson("Поля").encodeToByteArray()
        assertContentEquals(data, gunzip(gzipWithFields(data), limit = MAX_BOOK_BYTES))
    }

    @Test
    fun membersOneAfterAnotherReadAsOne() {
        val first = "first half, ".encodeToByteArray()
        val second = "second half".encodeToByteArray()
        assertEquals("first half, second half", gunzip(gzip(first) + gzip(second), limit = 1000).decodeToString())
    }

    @Test
    fun theMetaIsReadWithoutTheRestOfTheArchive() {
        val big = bookJson("Большая", paragraphs = 3000)
        val whole = archive("big", book = big)
        // Cut far into book.json: what is left cannot be read whole, but its head holds all of meta.json.
        val head = whole.copyOf(whole.size / 3)
        assertEquals("big", readArchiveMeta(head).id)
        assertEquals(ArchiveProblem.Damaged, assertFailsWith<BadArchive> { readArchive(head) }.problem)
    }

    @Test
    fun aBrokenArchiveIsDamaged() {
        val whole = archive("dogs")
        val truncated = whole.copyOf(whole.size - 10)
        assertEquals(ArchiveProblem.Damaged, assertFailsWith<BadArchive> { readArchive(truncated) }.problem)
        // The CRC at the end, wrong.
        val wrongCrc = whole.copyOf().also { it[it.size - 6] = (it[it.size - 6] + 1).toByte() }
        assertEquals(ArchiveProblem.Damaged, assertFailsWith<BadArchive> { readArchive(wrongCrc) }.problem)
        // A byte in the middle of the compressed stream: some check catches it, whichever comes first.
        val flipped = whole.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x5a).toByte() }
        assertFailsWith<BadArchive> { readArchive(flipped) }
    }

    @Test
    fun aPageOrAnEmptyAnswerIsNotABook() {
        for (bytes in listOf(ByteArray(0), "<html><body>Not found</body></html>".encodeToByteArray(), byteArrayOf(0x1f))) {
            assertEquals(ArchiveProblem.NotABook, assertFailsWith<BadArchive> { readArchive(bytes) }.problem)
        }
        // gzip, but not of a tar.
        assertEquals(ArchiveProblem.NotABook, assertFailsWith<BadArchive> { readArchive(gzip("just text".encodeToByteArray())) }.problem)
    }

    @Test
    fun anArchiveThatIsNotOfTwoBookFilesIsNotABook() {
        val book = bookJson("Порядок").encodeToByteArray()
        val meta = """{"format":"english-book","version":1,"id":"order","book":1,"title":"Порядок"}""".encodeToByteArray()
        val swapped = gzip(tar("book.json" to book, "meta.json" to meta))
        assertEquals(ArchiveProblem.NotABook, assertFailsWith<BadArchive> { readArchive(swapped) }.problem)
        assertEquals(ArchiveProblem.NotABook, assertFailsWith<BadArchive> { readArchiveMeta(swapped) }.problem)
        val alone = gzip(tar("meta.json" to meta))
        assertEquals(ArchiveProblem.NotABook, assertFailsWith<BadArchive> { readArchive(alone) }.problem)
    }

    @Test
    fun theMetaSaysWhetherThisReaderCanTakeIt() {
        fun problem(meta: String) = assertFailsWith<BadArchive> { readArchiveMeta(archive("x", metaOverride = meta)) }.problem
        assertEquals(ArchiveProblem.NotABook, problem("""{"format":"epub","version":1,"id":"x"}"""))
        assertEquals(ArchiveProblem.NotABook, problem("""{"format":"english-book","version":1,"id":"../etc"}"""))
        assertEquals(ArchiveProblem.NotABook, problem("""{"format":"english-book","id":"x"}"""))
        assertEquals(ArchiveProblem.NotABook, problem("not json"))
        assertEquals(ArchiveProblem.Newer, problem("""{"format":"english-book","version":2,"id":"x"}"""))
        assertEquals(ArchiveProblem.Newer, problem("""{"format":"english-book","version":1,"book":2,"id":"x"}"""))
        // Optional fields may be missing: the title falls back to the id.
        val bare = readArchiveMeta(archive("x", metaOverride = """{"format":"english-book","version":1,"id":"x"}"""))
        assertEquals("x", bare.title)
    }

    @Test
    fun aBookOfAnotherSizeThanTheMetaSaysIsDamaged() {
        val meta = """{"format":"english-book","version":1,"id":"x","size":5}"""
        assertEquals(ArchiveProblem.Damaged, assertFailsWith<BadArchive> { readArchive(archive("x", metaOverride = meta)) }.problem)
    }

    @Test
    fun aTarHeaderWithAWrongChecksumIsDamaged() {
        val raw = tar("meta.json" to "{}".encodeToByteArray())
        raw[148] = '7'.code.toByte()
        assertEquals(ArchiveProblem.Damaged, assertFailsWith<BadArchive> { tarEntries(raw) }.problem)
    }

    @Test
    fun anArchiveThatUnpacksTooFarIsStopped() {
        val zeros = gzip(ByteArray(2_000_000))
        assertTrue(zeros.size < 10_000)
        assertEquals(ArchiveProblem.TooLarge, assertFailsWith<BadArchive> { gunzip(zeros, limit = 1_000_000) }.problem)
        assertEquals(2_000_000, gunzip(zeros, limit = 2_000_000).size)
    }
}

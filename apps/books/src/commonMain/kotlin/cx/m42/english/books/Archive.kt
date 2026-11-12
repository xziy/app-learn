package cx.m42.english.books

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/*
 * A book as it travels: `<id>.book.tgz`, which the dragunkin-text skill's `pack_book.py` writes (its SKILL.md,
 * «Архив для загрузки»). A tar inside gzip, with two files in this order:
 *
 *   meta.json  what the book is, so the shelf shows it without unpacking the whole of it
 *   book.json  the book itself, as `parseBook` reads it
 *
 * Read here in plain Kotlin — gzip, DEFLATE and tar are small, and with no platform part every target reads a book
 * the same way.
 */

/** The `format` of every book archive. */
internal const val ARCHIVE_FORMAT = "english-book"

/** The newest archive this reader understands: `ARCHIVE_VERSION` in `pack_book.py`. */
internal const val ARCHIVE_VERSION = 1

/** The newest `book.json` this reader understands: `BOOK_VERSION` in the skill's `book.py`. */
internal const val BOOK_VERSION = 1

/** How a book archive's file name ends: the shelf takes these from the folder and leaves the rest. */
internal const val ARCHIVE_SUFFIX = ".book.tgz"

/** What a book unpacks to at most; the same limit as `pack_book.py`, against an archive that unpacks into gigabytes. */
internal const val MAX_BOOK_BYTES = 64 * 1024 * 1024

/** `meta.json` comes first and is small: this much is unpacked to read it, and no more. */
private const val META_ROOM = 64 * 1024

private val ID = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

/** Why an archive is not a book this reader can take. */
internal enum class ArchiveProblem {
    /** Not gzip, not tar, or not the two files of a book. */
    NotABook,

    /** Broken on the way: a checksum does not match, or it ends too soon. */
    Damaged,

    /** Made for a newer reader than this one. */
    Newer,

    /** Unpacks to more than [MAX_BOOK_BYTES]. */
    TooLarge,
}

internal class BadArchive(val problem: ArchiveProblem, message: String) : Exception(message)

/** What `meta.json` says of a book. */
internal data class ArchiveMeta(
    val id: String,
    val title: String,
    val author: String = "",
    val transcription: Boolean = true,
    val paragraphs: Int = 0,
    val sentences: Int = 0,
    val translated: Int = 0,
    val size: Long = 0,
    /** Of `book.json`: the same book packed twice has the same one, so it tells a new version from the one on the shelf. */
    val sha256: String = "",
)

/** Reads only `meta.json`: the start of the archive, unpacked no further than it has to be. */
internal fun readArchiveMeta(archive: ByteArray): ArchiveMeta {
    val start = gunzip(archive, limit = MAX_BOOK_BYTES, stopAt = META_ROOM)
    val first = tarEntries(start, upTo = 1).firstOrNull() ?: throw BadArchive(ArchiveProblem.NotABook, "an empty archive")
    if (first.first != "meta.json") throw BadArchive(ArchiveProblem.NotABook, "the first file is ${first.first}, not meta.json")
    return parseMeta(first.second)
}

/** The whole archive: its `meta.json` and the text of its `book.json`, every checksum checked. */
internal fun readArchive(archive: ByteArray): Pair<ArchiveMeta, String> {
    val entries = tarEntries(gunzip(archive, limit = MAX_BOOK_BYTES))
    val names = entries.map { it.first }
    if (names != listOf("meta.json", "book.json")) throw BadArchive(ArchiveProblem.NotABook, "the archive has $names")
    val meta = parseMeta(entries[0].second)
    val book = entries[1].second
    if (meta.size > 0 && meta.size != book.size.toLong()) {
        throw BadArchive(ArchiveProblem.Damaged, "book.json is ${book.size} bytes, meta.json says ${meta.size}")
    }
    return meta to book.decodeToString()
}

private fun parseMeta(bytes: ByteArray): ArchiveMeta {
    val root = try {
        Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
    } catch (e: Exception) {
        null
    } ?: throw BadArchive(ArchiveProblem.NotABook, "meta.json is not a JSON object")
    fun text(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull
    fun int(key: String) = (root[key] as? JsonPrimitive)?.intOrNull
    if (text("format") != ARCHIVE_FORMAT) throw BadArchive(ArchiveProblem.NotABook, "the format is ${text("format")}")
    val version = int("version") ?: throw BadArchive(ArchiveProblem.NotABook, "meta.json has no version")
    if (version > ARCHIVE_VERSION) throw BadArchive(ArchiveProblem.Newer, "archive version $version")
    if ((int("book") ?: 1) > BOOK_VERSION) throw BadArchive(ArchiveProblem.Newer, "book version ${int("book")}")
    val id = text("id")?.takeIf { ID.matches(it) } ?: throw BadArchive(ArchiveProblem.NotABook, "the id is ${text("id")}")
    return ArchiveMeta(
        id = id,
        title = text("title")?.ifBlank { null } ?: id,
        author = text("author").orEmpty(),
        transcription = (root["transcription"] as? JsonPrimitive)?.booleanOrNull != false,
        paragraphs = int("paragraphs") ?: 0,
        sentences = int("sentences") ?: 0,
        translated = int("translated") ?: 0,
        size = (root["size"] as? JsonPrimitive)?.longOrNull ?: 0,
        sha256 = text("sha256").orEmpty(),
    )
}

// ------------------------------------------------------------ tar

/**
 * The regular files of a tar, by name, in order; [upTo] stops after that many. Other entries — folders, pax and GNU
 * headers — are passed over. Each header's checksum is checked, so a header that is not one is caught here.
 */
internal fun tarEntries(data: ByteArray, upTo: Int = Int.MAX_VALUE): List<Pair<String, ByteArray>> {
    val out = mutableListOf<Pair<String, ByteArray>>()
    var at = 0
    while (out.size < upTo) {
        if (at + 512 > data.size) {
            if (at == data.size || out.isNotEmpty()) break
            throw BadArchive(ArchiveProblem.NotABook, "not a tar")
        }
        if ((at until at + 512).all { data[it].toInt() == 0 }) break
        val stored = octal(data, at + 148, 8) ?: throw BadArchive(ArchiveProblem.NotABook, "not a tar header")
        var sum = 0L
        for (i in at until at + 512) sum += if (i in at + 148 until at + 156) 32 else data[i].toInt() and 0xff
        if (sum != stored) throw BadArchive(ArchiveProblem.Damaged, "a tar header's checksum does not match")
        val size = octal(data, at + 124, 12) ?: throw BadArchive(ArchiveProblem.NotABook, "a tar entry without a size")
        val type = data[at + 156].toInt().toChar()
        val name = cString(data, at, 100)
        val prefix = if (cString(data, at + 257, 5) == "ustar") cString(data, at + 345, 155) else ""
        val body = at + 512
        val end = body + size
        if (size > MAX_BOOK_BYTES || end > data.size) throw BadArchive(ArchiveProblem.Damaged, "the archive ends inside $name")
        if (type == '0' || type == '\u0000') {
            out += (if (prefix.isEmpty()) name else "$prefix/$name") to data.copyOfRange(body, end.toInt())
        }
        at = (body + (size + 511) / 512 * 512).toInt()
    }
    return out
}

private fun cString(data: ByteArray, from: Int, length: Int): String {
    var end = from
    while (end < from + length && data[end].toInt() != 0) end++
    return data.copyOfRange(from, end).decodeToString()
}

/** A tar number: octal digits, padded with spaces or NULs on either side. */
private fun octal(data: ByteArray, from: Int, length: Int): Long? {
    var value = 0L
    var digits = 0
    for (i in from until from + length) {
        val c = data[i].toInt().toChar()
        when {
            c in '0'..'7' -> {
                value = value * 8 + (c - '0')
                digits++
            }
            c == ' ' || c == '\u0000' -> if (digits > 0) break
            else -> return null
        }
    }
    return if (digits > 0) value else null
}

// ------------------------------------------------------------ gzip

/**
 * Unpacks gzip (RFC 1952), every member of it, and checks each member's CRC-32 and length. Throws [BadArchive] with
 * [ArchiveProblem.TooLarge] past [limit] bytes. With [stopAt], returns as soon as that many bytes are out, without
 * the checks at the end: enough to read the head of a tar.
 */
internal fun gunzip(data: ByteArray, limit: Int, stopAt: Int = Int.MAX_VALUE): ByteArray {
    val out = Output(limit, stopAt)
    var at = 0
    do {
        at = member(data, at, out) ?: return out.bytes()
    } while (at < data.size && data[at].toInt() == 0x1f)
    return out.bytes()
}

/** One gzip member from [start]; where the next one starts, or null when [Output.stopAt] was reached. */
private fun member(data: ByteArray, start: Int, out: Output): Int? {
    fun byte(i: Int) = if (i < data.size) data[i].toInt() and 0xff else throw BadArchive(ArchiveProblem.Damaged, "gzip ends too soon")
    // A header and a trailer are 18 bytes: anything shorter is not gzip at all, say an empty page.
    if (data.size - start < 18 || byte(start) != 0x1f || byte(start + 1) != 0x8b || byte(start + 2) != 8) {
        throw BadArchive(ArchiveProblem.NotABook, "not gzip")
    }
    val flags = byte(start + 3)
    var at = start + 10
    if (flags and 4 != 0) at += 2 + (byte(at) or (byte(at + 1) shl 8))
    if (flags and 8 != 0) { while (byte(at) != 0) at++; at++ }
    if (flags and 16 != 0) { while (byte(at) != 0) at++; at++ }
    if (flags and 2 != 0) at += 2
    val from = out.size
    val inflater = Inflater(data, at, out)
    if (!inflater.run()) return null
    at = inflater.end()
    val crc = (0..3).fold(0L) { acc, i -> acc or (byte(at + i).toLong() shl (8 * i)) }
    val length = (0..3).fold(0L) { acc, i -> acc or (byte(at + 4 + i).toLong() shl (8 * i)) }
    if (crc != out.crc32(from) || length != ((out.size - from).toLong() and 0xffffffffL)) {
        throw BadArchive(ArchiveProblem.Damaged, "gzip's checksum does not match")
    }
    return at + 8
}

/** The unpacked bytes, grown as needed, with the two limits. */
private class Output(private val limit: Int, val stopAt: Int) {
    private var buffer = ByteArray(64 * 1024)
    var size = 0
        private set

    /** False once [stopAt] is reached: the caller stops there. */
    fun put(b: Int): Boolean {
        if (size >= limit) throw BadArchive(ArchiveProblem.TooLarge, "unpacks to more than $limit bytes")
        if (size == buffer.size) buffer = buffer.copyOf(minOf(limit, buffer.size * 2).coerceAtLeast(size + 1))
        buffer[size++] = b.toByte()
        return size < stopAt
    }

    /** A back-reference of DEFLATE: [length] bytes from [distance] back, which may overlap what it writes. */
    fun copy(distance: Int, length: Int): Boolean {
        if (distance > size) throw BadArchive(ArchiveProblem.Damaged, "a reference before the start")
        for (i in 0 until length) if (!put(buffer[size - distance].toInt())) return false
        return true
    }

    fun bytes(): ByteArray = buffer.copyOf(size)

    fun crc32(from: Int): Long {
        var crc = 0xffffffffL.toInt()
        for (i in from until size) crc = CRC_TABLE[(crc xor buffer[i].toInt()) and 0xff] xor (crc ushr 8)
        return crc.inv().toLong() and 0xffffffffL
    }
}

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0xedb88320L.toInt() xor (c ushr 1)) else c ushr 1 }
    c
}

/** A canonical Huffman code as DEFLATE builds it: how many codes of each length, and the symbols in code order. */
private class Huffman(val count: IntArray, val symbol: IntArray)

/** DEFLATE (RFC 1951), after Mark Adler's puff: plain, checked at every step, fast enough for a book. */
private class Inflater(private val data: ByteArray, private var at: Int, private val out: Output) {
    private var bitBuffer = 0
    private var bitCount = 0

    /** Where the stream ended, at a whole byte. */
    fun end(): Int = at

    private fun bits(need: Int): Int {
        var value = bitBuffer
        while (bitCount < need) {
            if (at >= data.size) throw BadArchive(ArchiveProblem.Damaged, "DEFLATE ends too soon")
            value = value or ((data[at++].toInt() and 0xff) shl bitCount)
            bitCount += 8
        }
        bitBuffer = value ushr need
        bitCount -= need
        return value and ((1 shl need) - 1)
    }

    /** True when the last block ended; false when [Output.stopAt] stopped it. */
    fun run(): Boolean {
        do {
            val last = bits(1) == 1
            val more = when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LENGTHS, FIXED_DISTANCES)
                2 -> dynamic()
                else -> throw BadArchive(ArchiveProblem.Damaged, "a DEFLATE block of type 3")
            }
            if (!more) return false
        } while (!last)
        return true
    }

    private fun stored(): Boolean {
        bitBuffer = 0
        bitCount = 0
        if (at + 4 > data.size) throw BadArchive(ArchiveProblem.Damaged, "DEFLATE ends too soon")
        val length = (data[at].toInt() and 0xff) or ((data[at + 1].toInt() and 0xff) shl 8)
        val check = (data[at + 2].toInt() and 0xff) or ((data[at + 3].toInt() and 0xff) shl 8)
        if (length != (check.inv() and 0xffff)) throw BadArchive(ArchiveProblem.Damaged, "a stored block's length")
        at += 4
        if (at + length > data.size) throw BadArchive(ArchiveProblem.Damaged, "DEFLATE ends too soon")
        for (i in 0 until length) if (!out.put(data[at++].toInt() and 0xff)) return false
        return true
    }

    private fun decode(code: Huffman): Int {
        var value = 0
        var first = 0
        var index = 0
        for (length in 1 until MAX_BITS + 1) {
            value = value or bits(1)
            val count = code.count[length]
            if (value - count < first) return code.symbol[index + (value - first)]
            index += count
            first = (first + count) shl 1
            value = value shl 1
        }
        throw BadArchive(ArchiveProblem.Damaged, "a code DEFLATE does not have")
    }

    private fun codes(lengths: Huffman, distances: Huffman): Boolean {
        while (true) {
            var symbol = decode(lengths)
            when {
                symbol < 256 -> if (!out.put(symbol)) return false
                symbol == 256 -> return true
                else -> {
                    symbol -= 257
                    if (symbol >= 29) throw BadArchive(ArchiveProblem.Damaged, "a length DEFLATE does not have")
                    val length = LENGTH_BASE[symbol] + bits(LENGTH_EXTRA[symbol])
                    val d = decode(distances)
                    if (d >= 30) throw BadArchive(ArchiveProblem.Damaged, "a distance DEFLATE does not have")
                    val distance = DISTANCE_BASE[d] + bits(DISTANCE_EXTRA[d])
                    if (!out.copy(distance, length)) return false
                }
            }
        }
    }

    private fun dynamic(): Boolean {
        val lengthCount = bits(5) + 257
        val distanceCount = bits(5) + 1
        val codeCount = bits(4) + 4
        if (lengthCount > 286 || distanceCount > 30) throw BadArchive(ArchiveProblem.Damaged, "too many DEFLATE codes")
        val lengths = IntArray(MAX_CODES)
        for (i in 0 until codeCount) lengths[ORDER[i]] = bits(3)
        val lengthCode = huffman(lengths, 19)
        if (lengthCode.second != 0) throw BadArchive(ArchiveProblem.Damaged, "an incomplete DEFLATE code")
        var i = 0
        val all = IntArray(MAX_CODES)
        while (i < lengthCount + distanceCount) {
            val symbol = decode(lengthCode.first)
            if (symbol < 16) {
                all[i++] = symbol
            } else {
                val (value, repeat) = when (symbol) {
                    16 -> {
                        if (i == 0) throw BadArchive(ArchiveProblem.Damaged, "a repeat with nothing before it")
                        all[i - 1] to 3 + bits(2)
                    }
                    17 -> 0 to 3 + bits(3)
                    else -> 0 to 11 + bits(7)
                }
                if (i + repeat > lengthCount + distanceCount) throw BadArchive(ArchiveProblem.Damaged, "too many DEFLATE lengths")
                repeat(repeat) { all[i++] = value }
            }
        }
        if (all[256] == 0) throw BadArchive(ArchiveProblem.Damaged, "DEFLATE codes without an end")
        val lengthTable = huffman(all, lengthCount)
        if (lengthTable.second < 0 || (lengthTable.second > 0 && lengthCount - lengthTable.first.count[0] != 1)) {
            throw BadArchive(ArchiveProblem.Damaged, "an incomplete DEFLATE code")
        }
        val distanceTable = huffman(all.copyOfRange(lengthCount, lengthCount + distanceCount), distanceCount)
        if (distanceTable.second < 0 || (distanceTable.second > 0 && distanceCount - distanceTable.first.count[0] != 1)) {
            throw BadArchive(ArchiveProblem.Damaged, "an incomplete DEFLATE code")
        }
        return codes(lengthTable.first, distanceTable.first)
    }

    companion object {
        const val MAX_BITS = 15
        const val MAX_CODES = 286 + 30

        val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DISTANCE_BASE = intArrayOf(
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
            6145, 8193, 12289, 16385, 24577,
        )
        val DISTANCE_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

        val FIXED_LENGTHS: Huffman = huffman(IntArray(288) { if (it < 144) 8 else if (it < 256) 9 else if (it < 280) 7 else 8 }, 288).first
        val FIXED_DISTANCES: Huffman = huffman(IntArray(30) { 5 }, 30).first

        /**
         * Builds the code for the first [n] of [lengths]. The second value is what is left of the code space: 0 when
         * it is complete, more when it is not, less when it is over-subscribed — which callers judge as RFC 1951 does.
         */
        fun huffman(lengths: IntArray, n: Int): Pair<Huffman, Int> {
            val count = IntArray(MAX_BITS + 1)
            for (s in 0 until n) count[lengths[s]]++
            val symbol = IntArray(n)
            if (count[0] == n) return Huffman(count, symbol) to 0
            var left = 1
            for (length in 1..MAX_BITS) {
                left = (left shl 1) - count[length]
                if (left < 0) return Huffman(count, symbol) to left
            }
            val offsets = IntArray(MAX_BITS + 1)
            for (length in 1 until MAX_BITS) offsets[length + 1] = offsets[length] + count[length]
            for (s in 0 until n) if (lengths[s] != 0) symbol[offsets[lengths[s]]++] = s
            return Huffman(count, symbol) to left
        }
    }
}

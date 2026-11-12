package cx.m42.english.books

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/*
 * Archives made the JVM's way — java.util.zip is zlib, as Python's gzip is — so the reader's own gzip and tar are
 * checked against an independent writer. `cat.book.tgz` among the resources is the skill's own `pack_book.py`.
 */

/** A ustar archive of [files], in order, the way Python's tarfile writes one. */
internal fun tar(vararg files: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    for ((name, body) in files) {
        val header = ByteArray(512)
        fun put(at: Int, text: String) = text.encodeToByteArray().copyInto(header, at)
        put(0, name)
        put(100, "0000644\u0000")
        put(108, "0000000\u0000")
        put(116, "0000000\u0000")
        put(124, body.size.toString(8).padStart(11, '0') + "\u0000")
        put(136, "00000000000\u0000")
        header[156] = '0'.code.toByte()
        put(257, "ustar\u0000")
        put(263, "00")
        for (i in 148 until 156) header[i] = ' '.code.toByte()
        val sum = header.sumOf { it.toInt() and 0xff }
        put(148, sum.toString(8).padStart(6, '0') + "\u0000 ")
        out.write(header)
        out.write(body)
        out.write(ByteArray((512 - body.size % 512) % 512))
    }
    out.write(ByteArray(1024))
    return out.toByteArray()
}

/** gzip at [level]; [Deflater.NO_COMPRESSION] gives stored blocks, the default dynamic Huffman ones. */
internal fun gzip(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
    val out = ByteArrayOutputStream()
    object : GZIPOutputStream(out) {
        init {
            def.setLevel(level)
        }
    }.use { it.write(data) }
    return out.toByteArray()
}

/**
 * gzip by hand, with the optional header fields a writer may add — a name, an extra field, a comment, a header CRC —
 * around raw DEFLATE at [level] and [strategy].
 */
internal fun gzipWithFields(data: ByteArray, level: Int = 9, strategy: Int = Deflater.DEFAULT_STRATEGY): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x1f, 0x8b.toByte(), 8, (2 or 4 or 8 or 16).toByte(), 0, 0, 0, 0, 2, 3))
    out.write(byteArrayOf(3, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()))
    out.write("book.tar".encodeToByteArray() + 0.toByte())
    out.write("a comment".encodeToByteArray() + 0.toByte())
    out.write(byteArrayOf(0, 0))
    val deflater = Deflater(level, true).apply { setStrategy(strategy) }
    deflater.setInput(data)
    deflater.finish()
    val buffer = ByteArray(8192)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    val crc = CRC32().apply { update(data) }.value
    for (i in 0..3) out.write(((crc shr (8 * i)) and 0xff).toInt())
    for (i in 0..3) out.write((data.size shr (8 * i)) and 0xff)
    return out.toByteArray()
}

internal fun sha256(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

/** A book of [paragraphs] paragraphs, one sentence each, its title [title]. */
internal fun bookJson(title: String, paragraphs: Int = 3, word: String = "dog"): String {
    val body = (1..paragraphs).joinToString(",") { n ->
        """{"n":$n,"sentences":[{"ours":"Фраза $n.","thought":"Фраза $n.","english":"The $word $n.","transcription":"[ðэ $word]",
           "nouns":[{"word":"$word","meaning":"собака","form":"$word","thought":"Фраза","ours":"Фраза"}]}]}"""
    }
    return """{"book":1,"title":"$title","author":"Автор","transcription":true,"sentences":$paragraphs,"translated":$paragraphs,
        "paragraphs":[$body],"words":{}}"""
}

/** A book archive as `pack_book.py` makes it, with whatever [meta] says and [book] holds. */
internal fun archive(
    id: String,
    title: String = id.replaceFirstChar { it.uppercase() },
    book: String = bookJson(title),
    metaOverride: String? = null,
): ByteArray {
    val data = book.encodeToByteArray()
    val meta = metaOverride ?: """{"format":"english-book","version":1,"id":"$id","book":1,"title":"$title","author":"Автор",
        "transcription":true,"paragraphs":3,"sentences":3,"translated":3,"size":${data.size},"sha256":"${sha256(data)}"}"""
    return gzip(tar("meta.json" to meta.encodeToByteArray(), "book.json" to data))
}

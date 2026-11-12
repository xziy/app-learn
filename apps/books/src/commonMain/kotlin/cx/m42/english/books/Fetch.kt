package cx.m42.english.books

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The most an archive may weigh as it is downloaded: a book is well under a megabyte. */
internal const val MAX_ARCHIVE_BYTES: Long = 32L * 1024 * 1024

/** What the download screen holds: the link as typed, pasted or scanned, and how its download went. */
internal data class Fetch(val link: String = "", val state: Fetching = Fetching.Idle)

internal sealed interface Fetching {
    data object Idle : Fetching

    data object Working : Fetching

    data class Done(val book: BookInfo, val outcome: Outcome) : Fetching

    data class Failed(val problem: FetchProblem, val status: Int? = null) : Fetching
}

internal enum class Outcome {
    /** A book the shelf did not have. */
    Added,

    /** A new version of one it had. */
    Updated,

    /** The very same book: nothing was written. */
    Same,
}

internal enum class FetchProblem {
    /** Not a link at all. */
    NotALink,

    /** No answer: offline, a name that does not resolve, a refused connection. */
    NoAnswer,

    /** An answer other than 2xx; the status goes with it. */
    Status,

    NotABook,
    Damaged,
    Newer,
    TooLarge,

    /** Downloaded and good, but the folder would not take it. */
    CannotSave,

    /** The host has no downloader or no folder for this app. */
    Unavailable,
}

internal fun ArchiveProblem.asFetchProblem(): FetchProblem = when (this) {
    ArchiveProblem.NotABook -> FetchProblem.NotABook
    ArchiveProblem.Damaged -> FetchProblem.Damaged
    ArchiveProblem.Newer -> FetchProblem.Newer
    ArchiveProblem.TooLarge -> FetchProblem.TooLarge
}

/**
 * The address of a book archive in what a person pasted or a camera read; null when there is none. Three shapes:
 *
 *  - the archive's own address, `https://…/phosphorus.book.tgz`;
 *  - an activation of this app, `<scheme>://activate?a=books&c=<config>`, as `pack_book.py --scheme` writes it into
 *    a QR code — `c` is base64url or plain JSON with `download` in it;
 *  - this app's deep link, `<scheme>://app/books/download?url=<address>`.
 *
 * Whatever the shape, what comes out is an http or https address: a link can name only a download, never a file on
 * the device or some other scheme.
 */
internal fun bookLink(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    if (isWeb(trimmed)) return trimmed
    val scheme = trimmed.substringBefore("://", "")
    if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() || it in "+-." }) return null
    val rest = trimmed.substringAfter("://")
    val path = rest.substringBefore('?').trimEnd('/')
    val query = query(rest.substringAfter('?', ""))
    val address = when (path) {
        "activate" -> if ((query["a"] ?: query["appId"]) == APP_ID) configLink(query["c"] ?: query["config"]) else null
        "app/$APP_ID/$DOWNLOAD_PATH" -> query["url"]
        else -> null
    }
    return address?.trim()?.takeIf(::isWeb)
}

internal const val APP_ID = "books"

/** The deep link that opens the download screen: `<scheme>://app/books/download?url=…`. */
internal const val DOWNLOAD_PATH = "download"

private fun isWeb(text: String): Boolean {
    val lower = text.lowercase()
    val rest = when {
        lower.startsWith("https://") -> text.drop(8)
        lower.startsWith("http://") -> text.drop(7)
        else -> return false
    }
    return rest.substringBefore('/').isNotEmpty()
}

/** The `download` of a config, base64url first and then plain JSON, as the host reads an activation. */
private fun configLink(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val decoded = base64Url(raw)
    for (candidate in listOfNotNull(decoded, raw)) {
        val obj = runCatching { Json.parseToJsonElement(candidate) as? JsonObject }.getOrNull() ?: continue
        return (obj["download"] as? JsonPrimitive)?.contentOrNull
    }
    return null
}

private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun base64Url(value: String): String? {
    val clean = value.trimEnd('=')
    if (!clean.all { it in BASE64_URL }) return null
    var buffer = 0
    var bits = 0
    val out = ArrayList<Byte>(clean.length * 3 / 4 + 1)
    for (c in clean) {
        buffer = (buffer shl 6) or BASE64_URL.indexOf(c)
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xFF).toByte())
        }
    }
    return out.toByteArray().decodeToString()
}

/** `k=v&k2=v2`, percent-decoded; the first of repeated keys, as the host keeps it. A `+` stays a `+`. */
private fun query(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (pair in text.split('&')) {
        val name = pair.substringBefore('=')
        if (name.isNotEmpty() && name !in out) out[name] = percentDecode(pair.substringAfter('=', ""))
    }
    return out
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val hex = if (value[i] == '%' && i + 2 < value.length) {
            value.substring(i + 1, i + 3).takeIf { pair -> pair.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } }?.toInt(16)
        } else {
            null
        }
        if (hex != null) {
            bytes.add(hex.toByte())
            i += 3
        } else {
            value[i].toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}

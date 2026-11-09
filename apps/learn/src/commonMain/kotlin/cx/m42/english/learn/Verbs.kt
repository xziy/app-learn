package cx.m42.english.learn

import cx.m42.english.learn.resources.Res
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** One example of a verb, in the order a person goes through it. */
internal data class Sentence(
    /** The ordinary Russian phrase: how we think. */
    val ours: String,
    /** The Russian phrase built the English way: how the English think. */
    val thought: String,
    val english: String,
    /** In Russian letters, with `**` around the stressed vowel. */
    val transcription: String,
    /** Where it is in its verb's file, as the nouns point to it: `active.simple.past.0`. */
    val address: String = "",
    /** The nouns of the English phrase, as the base lists them. */
    val nouns: List<NounUse> = emptyList(),
)

/**
 * A noun as it stands in a phrase: the word in its dictionary form, its sense in the dictionary (null for the main
 * one), what it means here and how it is written in this phrase: `child`, null, `ребёнок`, `children`.
 */
internal data class NounUse(val word: String, val sense: String?, val meaning: String, val form: String) {
    /** What its marks are kept under. */
    val key: String get() = wordKey(word, sense)
}

/** One way to write and say a form: `abode`, `[эб**о**уд]`. */
internal data class WordForm(val word: String, val transcription: String)

/** The forms of a verb, in the order they are read, by their names in the base. */
internal enum class FormKind(val key: String) {
    Base("base"),
    Past("past"),
    Participle("participle"),
    Ing("ing"),
    S("s"),
}

internal enum class Voice(val key: String) { Active("active"), Passive("passive") }

internal enum class Aspect(val key: String) {
    Simple("simple"),
    Continuous("continuous"),
    Perfect("perfect"),
    PerfectContinuous("perfect_continuous"),
}

internal enum class Time(val key: String) { Past("past"), Present("present"), Future("future") }

/**
 * A tense of a verb, by its place in the course. No voice: the future in the past, which stands apart.
 *
 * Where a tense does not exist for a verb, [notPossible] says why and there are no sentences. The base can
 * close a whole voice or a whole aspect at once, and then what is below it is null too.
 */
internal data class Tense(
    val voice: Voice? = null,
    val aspect: Aspect? = null,
    val time: Time? = null,
    /** This verb in this tense: `am/is/are abandoning`. */
    val form: String? = null,
    val sentences: List<Sentence> = emptyList(),
    val notPossible: String? = null,
)

/** A verb and its tenses, in the order of the course. */
internal data class Verb(
    val word: String,
    val meaning: String,
    val tenses: List<Tense>,
    /** What is particular about it: regular or not, with an object or not, whether it has a passive. */
    val notes: String = "",
    /** Usually one way per form, two where a verb has variants. A form the base does not give is absent. */
    val forms: Map<FormKind, List<WordForm>> = emptyMap(),
) {
    /** How the word itself is said, if the base gives it. */
    val transcription: String? get() = forms[FormKind.Base]?.firstOrNull()?.transcription
}

/** The theme that is open until the person chooses another. */
internal const val DefaultTheme: String = "general"

/** A theme of the base, as `verbs/themes.json` gives it: `yachting`, «Яхтинг: паруса и снасти…». */
internal data class Theme(val name: String, val description: String) {
    /** `office-work` as words. The base says nothing else about a theme in English. */
    val englishTitle: String get() = name.replace('-', ' ').replaceFirstChar { it.uppercase() }

    /** What stands before the colon of the description. */
    val russianTitle: String get() = if (':' in description) description.substringBefore(':').trim() else englishTitle

    /** What stands after it, or all of it if there is no colon. */
    val russianAbout: String get() = description.substringAfter(':').trim().replaceFirstChar { it.uppercase() }
}

/** Where the base of words comes from. */
internal interface VerbSource {
    /** The themes that have verbs. */
    suspend fun themes(): List<Theme>

    /** Every verb of a theme, in the order they are learned. */
    suspend fun index(theme: String): List<String>

    /** The JSON of a verb in a theme: the same verb has other phrases in another one. */
    suspend fun read(theme: String, word: String): String

    /** The dictionary, as `:apps:learn:syncLexicon` writes it. None at all is no reason for the cards not to work. */
    suspend fun lexicon(): String? = null
}

/** The base as the app carries it: `composeResources/files/verbs`, filled by `:apps:learn:syncVerbs`. */
internal object ResourceVerbSource : VerbSource {
    @OptIn(ExperimentalResourceApi::class)
    override suspend fun themes(): List<Theme> =
        parseThemes(Res.readBytes("files/verbs/themes.json").decodeToString())

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun index(theme: String): List<String> =
        Json.parseToJsonElement(Res.readBytes("files/verbs/$theme/index.json").decodeToString())
            .jsonArray.map { it.jsonPrimitive.content }

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun read(theme: String, word: String): String =
        Res.readBytes("files/verbs/$theme/$word.json").decodeToString()

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun lexicon(): String? =
        try {
            Res.readBytes("files/lexicon/nouns.json").decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}

internal fun parseThemes(text: String): List<Theme> =
    Json.parseToJsonElement(text).jsonObject.map { (name, description) -> Theme(name, description.jsonPrimitive.content) }

/**
 * Reads the JSON the dragunkin-verbs skill writes. Throws on anything that is not such a file, but a verb
 * without forms or notes still has its cards: those are there to read about, not to learn from.
 */
internal fun parseVerb(text: String): Verb {
    val root = Json.parseToJsonElement(text).jsonObject
    val nouns = root.nouns()
    fun JsonObject.tense(voice: Voice?, aspect: Aspect?, time: Time?): Tense? {
        val at = listOfNotNull(voice?.key, aspect?.key, time?.key).ifEmpty { listOf("future_in_past") }.joinToString(".")
        val sentences = examples(at, nouns)
        val closed = notPossible()
        if (sentences.isEmpty() && closed == null) return null
        return Tense(voice, aspect, time, this["form"]?.jsonPrimitive?.content, sentences, closed)
    }
    val tenses = buildList {
        for (voice in Voice.entries) {
            val ofVoice = root.child(voice.key) ?: continue
            val noVoice = ofVoice.notPossible()
            if (noVoice != null) {
                add(Tense(voice = voice, notPossible = noVoice))
                continue
            }
            for (aspect in Aspect.entries) {
                val ofAspect = ofVoice.child(aspect.key) ?: continue
                val noAspect = ofAspect.notPossible()
                if (noAspect != null) {
                    add(Tense(voice = voice, aspect = aspect, notPossible = noAspect))
                    continue
                }
                for (time in Time.entries) {
                    ofAspect.child(time.key)?.tense(voice, aspect, time)?.let(::add)
                }
            }
        }
        root.child("future_in_past")?.tense(null, null, null)?.let(::add)
    }
    return Verb(
        word = root.getValue("verb").jsonPrimitive.content,
        meaning = root.getValue("meaning").jsonPrimitive.content,
        tenses = tenses,
        notes = root["notes"]?.jsonPrimitive?.content.orEmpty(),
        forms = root.child("forms")?.forms().orEmpty(),
    )
}

private fun JsonObject.child(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.notPossible(): String? = this["not_possible"]?.jsonPrimitive?.content

/** The nouns of a verb by the phrase they stand in. A file without them, or with one that cannot be read, has none. */
private fun JsonObject.nouns(): Map<String, List<NounUse>> {
    val found = mutableMapOf<String, MutableList<NounUse>>()
    for (element in (this["nouns"] as? JsonArray).orEmpty()) {
        val noun = element as? JsonObject ?: continue
        val word = (noun["word"] as? JsonPrimitive)?.contentOrNull ?: continue
        val sense = (noun["sense"] as? JsonPrimitive)?.contentOrNull
        val meaning = (noun["meaning"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        for (link in (noun["used_in"] as? JsonArray).orEmpty()) {
            val at = link as? JsonObject ?: continue
            val phrase = (at["phrase"] as? JsonPrimitive)?.contentOrNull ?: continue
            val form = (at["form"] as? JsonPrimitive)?.contentOrNull ?: word
            found.getOrPut(phrase) { mutableListOf() } += NounUse(word, sense, meaning, form)
        }
    }
    return found
}

private fun JsonObject.forms(): Map<FormKind, List<WordForm>> =
    FormKind.entries.associateWith { kind ->
        this[kind.key]?.jsonArray.orEmpty().map { element ->
            val form = element.jsonObject
            WordForm(form.getValue("word").jsonPrimitive.content, form.getValue("transcription").jsonPrimitive.content)
        }
    }.filterValues { it.isNotEmpty() }

private fun JsonObject.examples(at: String, nouns: Map<String, List<NounUse>>): List<Sentence> =
    this["examples"]?.jsonArray.orEmpty().mapIndexed { index, element ->
        val example = element.jsonObject
        fun text(key: String) = example.getValue(key).jsonPrimitive.content
        val address = "$at.$index"
        Sentence(text("ours"), text("thought"), text("english"), text("transcription"), address, nouns[address].orEmpty())
    }

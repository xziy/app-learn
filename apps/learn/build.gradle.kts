import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Learning English: a full-screen feed of cards with sentences for a few verbs at a time. It sees only
 * its `AppRuntime` — no `superizer:host`, no host module, no other app. `commonMain` uses no JVM API,
 * so every target of the host gets the same code; speech is the one platform part (`Speech`).
 *
 * Android is here for the hosts that have it (Unitool); this repository's own host has none. There is no
 * wasmJs: speech has no web side, so a host with a web target leaves this app out there.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    explicitApi()

    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.superizer.core)
            implementation(libs.superizer.ui.theme)
            implementation(libs.superizer.ui)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(libs.superizer.testing)
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "cx.m42.english.learn"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.resources {
    packageOfResClass = "cx.m42.english.learn.resources"
}

/**
 * The base of words is `../verbs/<theme>/<verb>/<verb>.json`, written by the dragunkin-verbs skill, and
 * `../verbs/themes.json` lists the themes. This copies the themes that have verbs into the app, each with a list
 * of what is there, because resources cannot be listed at run time. The list goes in the order of the theme's
 * journal, the order they are learned in. The copy is part of the repository, so a checkout without `../verbs`
 * — CI — still builds with the words.
 *
 *     ./gradlew :apps:learn:syncVerbs
 */
val syncVerbs by tasks.registering {
    group = "learn"
    description = "Copies the themes of ../verbs, with their verbs, into this module's resources."

    val source = rootDir.resolve("../verbs")
    val target = layout.projectDirectory.dir("src/commonMain/composeResources/files/verbs").asFile
    // Captured here: with the configuration cache the script is long gone by the time this runs.
    val logger = logger

    doLast {
        check(source.isDirectory) { "No verbs at ${source.canonicalPath}" }
        val themes = JsonSlurper().parse(source.resolve("themes.json")) as Map<*, *>
        target.deleteRecursively()
        target.mkdirs()
        val filled = linkedMapOf<String, String>()
        for ((name, description) in themes) {
            val theme = source.resolve(name as String)
            val journal = theme.resolve("progress.json")
            val learned = if (!journal.isFile) {
                emptyList()
            } else {
                ((JsonSlurper().parse(journal) as Map<*, *>)["batches"] as List<*>)
                    .flatMap { (it as Map<*, *>)["verbs"] as List<*> }
            }
            val files = theme.listFiles().orEmpty()
                .filter { it.isDirectory }
                .mapNotNull { dir -> dir.resolve("${dir.name}.json").takeIf { it.isFile } }
                .sortedWith(
                    compareBy(
                        { file -> learned.indexOf(file.nameWithoutExtension).let { if (it < 0) Int.MAX_VALUE else it } },
                        { file -> file.name },
                    ),
                )
            if (files.isEmpty()) continue
            val copy = target.resolve(name).apply { mkdirs() }
            files.forEach { it.copyTo(copy.resolve(it.name)) }
            val names = files.joinToString(",\n") { "  \"${it.nameWithoutExtension}\"" }
            copy.resolve("index.json").writeText("[\n$names\n]\n")
            filled[name] = description as String
            logger.lifecycle("${files.size} verbs of $name")
        }
        val json = JsonGenerator.Options().disableUnicodeEscaping().build().toJson(filled)
        target.resolve("themes.json").writeText(JsonOutput.prettyPrint(json, true) + "\n")
    }
}

/**
 * The dictionary is `../lexicon/nouns/<word>.json`, written by the lexicon skill. This puts all of it in one file of
 * this module's resources, since they cannot be listed, and adds to each word what the phrases of `../verbs` say
 * about it: the verbs of each theme it stands in (`in`), the forms it has there (`forms`) and how many phrases each
 * of its senses has (`count`, "" being the main sense). Run it with `syncVerbs`, so that both copies are of one
 * moment.
 *
 *     ./gradlew :apps:learn:syncVerbs :apps:learn:syncLexicon
 */
val syncLexicon by tasks.registering {
    group = "learn"
    description = "Copies ../lexicon into this module's resources, with where each word stands in ../verbs."

    val lexicon = rootDir.resolve("../lexicon/nouns")
    val verbs = rootDir.resolve("../verbs")
    val target = layout.projectDirectory.file("src/commonMain/composeResources/files/lexicon/nouns.json").asFile
    val logger = logger

    doLast {
        check(lexicon.isDirectory) { "No dictionary at ${lexicon.canonicalPath}" }
        val themes = JsonSlurper().parse(verbs.resolve("themes.json")) as Map<*, *>
        val places = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        val forms = mutableMapOf<String, MutableSet<String>>()
        val counts = mutableMapOf<String, MutableMap<String, Int>>()
        for (name in themes.keys) {
            val theme = verbs.resolve(name as String)
            for (dir in theme.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
                val file = dir.resolve("${dir.name}.json")
                if (!file.isFile) continue
                val nouns = (JsonSlurper().parse(file) as Map<*, *>)["nouns"] as? List<*> ?: continue
                for (item in nouns) {
                    val noun = item as Map<*, *>
                    val word = noun["word"] as String
                    val links = noun["used_in"] as List<*>
                    val ofTheme = places.getOrPut(word) { linkedMapOf() }.getOrPut(name) { mutableListOf() }
                    if (dir.name !in ofTheme) ofTheme += dir.name
                    links.forEach { forms.getOrPut(word) { linkedSetOf() } += (it as Map<*, *>)["form"] as String }
                    val sense = noun["sense"] as String? ?: ""
                    counts.getOrPut(word) { linkedMapOf() }.merge(sense, links.size, Int::plus)
                }
            }
        }
        val words = lexicon.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.name }.map { file ->
            val entry = LinkedHashMap(JsonSlurper().parse(file) as Map<*, *>)
            val word = entry["word"] as String
            entry.remove("spec")
            entry.remove("class")
            entry["in"] = places[word].orEmpty()
            entry["forms"] = forms[word].orEmpty().toList()
            entry["count"] = counts[word].orEmpty()
            entry
        }
        target.parentFile.mkdirs()
        val json = JsonGenerator.Options().disableUnicodeEscaping().build().toJson(mapOf("spec" to 1, "words" to words))
        target.writeText(json + "\n")
        logger.lifecycle("${words.size} words, ${target.length() / 1024} KB")
    }
}

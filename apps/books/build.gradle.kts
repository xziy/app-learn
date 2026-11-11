import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Reading books: a full-screen reader of texts that the dragunkin-text skill has put the English way, each sentence
 * in Russian, in the English way of thinking and in English, with its nouns set off and translated. It sees only its
 * `AppRuntime` — no `superizer:host`, no host module, no other app. `commonMain` uses no JVM API and there is no
 * platform part, so every target gets the same code.
 *
 * Android is here for the hosts that have it (Unitool), as in `apps/learn`; this repository's own host has none.
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
    namespace = "cx.m42.english.books"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.resources {
    packageOfResClass = "cx.m42.english.books.resources"
}

/**
 * A book is `../texts/<name>/book.json`, which the dragunkin-text skill's `render_text.py` writes next to the md (its
 * SKILL.md, «Книга для читалки»). This copies every book into the app as `files/books/<name>.json` and lists them in
 * `files/books/index.json`, with what the shelf shows of each, because resources cannot be listed at run time and the
 * shelf should not have to read whole books. The copy is part of the repository, so a checkout without `../texts`
 * — CI — still builds with the books. A text without `book.json` (one that does not pass the skill's check) is left out.
 *
 *     ./gradlew :apps:books:syncBooks
 */
val syncBooks by tasks.registering {
    group = "books"
    description = "Copies the books of ../texts into this module's resources."

    val source = rootDir.resolve("../texts")
    val target = layout.projectDirectory.dir("src/commonMain/composeResources/files/books").asFile
    // Captured here: with the configuration cache the script is long gone by the time this runs.
    val logger = logger

    doLast {
        check(source.isDirectory) { "No texts at ${source.canonicalPath}" }
        val books = source.listFiles().orEmpty()
            .filter { it.isDirectory && it.resolve("book.json").isFile }
            .sortedBy { it.name }
        target.deleteRecursively()
        target.mkdirs()
        val index = books.map { dir ->
            val file = dir.resolve("book.json")
            val book = JsonSlurper().parse(file) as Map<*, *>
            file.copyTo(target.resolve("${dir.name}.json"))
            logger.lifecycle("${dir.name}: ${book["translated"]} of ${book["sentences"]} sentences, ${file.length() / 1024} KB")
            linkedMapOf(
                "slug" to dir.name,
                "title" to book["title"],
                "author" to (book["author"] ?: ""),
                "paragraphs" to (book["paragraphs"] as List<*>).size,
                "sentences" to book["sentences"],
                "translated" to book["translated"],
            )
        }
        val json = JsonGenerator.Options().disableUnicodeEscaping().build().toJson(index)
        target.resolve("index.json").writeText(JsonOutput.prettyPrint(json, true) + "\n")
        logger.lifecycle("${index.size} books")
    }
}

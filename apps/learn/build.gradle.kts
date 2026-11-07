import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Learning English: a full-screen feed of cards with sentences for a few verbs at a time. It sees only
 * its `AppRuntime` — no `superizer:host`, no host module, no other app. `commonMain` uses no JVM API,
 * so every target of the host gets the same code; speech is the one platform part (`Speech`).
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    explicitApi()

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

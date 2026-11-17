rootProject.name = "english"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

/**
 * The library, from its sources next door: this app is built *with* Superizer, and a change there
 * is visible here on the next build.
 *
 * Build files still write Maven coordinates (`cx.m42.superizer:core:…`), never `project(":core")`,
 * so `SUPERIZER_FROM_MAVEN=1` can resolve the same text as built artifacts instead, at the version
 * in `gradle/libs.versions.toml`: out of mavenLocal, then out of Superizer's registry on GitHub
 * Packages, where its `publish.yml` puts every release for every target. CI builds that way
 * (`.github/workflows/ios.yml`) and so compiles none of Superizer. Superizer is not on Maven
 * Central, so without the checkout and without that there is nothing to build against.
 */
val superizerFromMaven = System.getenv("SUPERIZER_FROM_MAVEN") == "1"
val superizerDir = file("../superizer")
if (!superizerFromMaven) {
    check(superizerDir.exists()) {
        "Superizer's sources are expected at ${superizerDir.canonicalPath}: " +
            "git clone git@github.com:adminization/superizer.git ${superizerDir.canonicalPath}"
    }
    includeBuild(superizerDir)
}

/**
 * GitHub Packages answers 401 to a download without a token, even from a public repository, so the
 * registry is asked only when one is given: `SuperizerPackagesUsername` and
 * `SuperizerPackagesPassword` (a classic PAT with `read:packages`) in `~/.gradle/gradle.properties`,
 * or as `ORG_GRADLE_PROJECT_SuperizerPackages…` in the environment, which is what CI sets. Gradle
 * reads them itself when it resolves, so the token never lands in the configuration cache; this
 * looks only at the user name, which is not a secret.
 */
val superizerRegistry = superizerFromMaven &&
    providers.gradleProperty("SuperizerPackagesUsername").isPresent

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        if (superizerRegistry) {
            maven("https://maven.pkg.github.com/adminization/superizer") {
                name = "SuperizerPackages"
                credentials(PasswordCredentials::class)
                // Only the library comes from here; everything else is not asked for with the token.
                mavenContent { includeGroupAndSubgroups("cx.m42.superizer") }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// The host. Apps go into apps/<name> next to it.
include(":composeApp")

// Learning English.
include(":apps:learn")

// Reading books the English way.
include(":apps:books")

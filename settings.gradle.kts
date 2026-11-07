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
 * so `SUPERIZER_FROM_MAVEN=1` can resolve the same text out of mavenLocal instead. Superizer is not
 * on Maven Central, so without the checkout and without that there is nothing to build against.
 */
val superizerDir = file("../superizer")
if (System.getenv("SUPERIZER_FROM_MAVEN") != "1") {
    check(superizerDir.exists()) {
        "Superizer's sources are expected at ${superizerDir.canonicalPath}: " +
            "git clone git@github.com:adminization/superizer.git ${superizerDir.canonicalPath}"
    }
    includeBuild(superizerDir)
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
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

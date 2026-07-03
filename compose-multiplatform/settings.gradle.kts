@file:Suppress("UnstableApiUsage")

rootProject.name = "V2rayNGCompose"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
    // Version declarations here are lazy: a plugin is only downloaded when it is
    // actually requested/applied. The Android plugins are applied conditionally
    // (see build.gradle.kts of each module) so a host without the Android SDK or
    // access to Google's Maven never needs to resolve them.
    plugins {
        id("com.android.application") version "8.7.3"
        id("com.android.library") version "8.7.3"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

include(":core")
include(":composeApp")

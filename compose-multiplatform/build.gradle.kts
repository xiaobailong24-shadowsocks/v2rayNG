// The Android Gradle Plugin is put on the buildscript classpath ONLY when
// ENABLE_ANDROID=true (CI android job). The modules apply it imperatively
// (pluginManager.apply), which requires it on the classpath. Gating on an env
// var keeps hosts without Google Maven access (the dev sandbox) from ever
// resolving AGP — they build :core + desktop without the Android target.
// Keep the version in sync with gradle/libs.versions.toml (agp).
buildscript {
    if (System.getenv("ENABLE_ANDROID") == "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}

// Kotlin / Compose plugins are safe to resolve on any host (Maven Central &
// Gradle Portal).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

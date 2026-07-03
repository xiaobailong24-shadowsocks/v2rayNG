// Root build script. Plugins are declared `apply false` only for those that are
// safe to resolve on any host (Kotlin / Compose from Maven Central & Gradle
// Portal). Android Gradle Plugin is intentionally NOT declared here to avoid
// forcing its resolution from Google Maven on hosts that cannot reach it.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

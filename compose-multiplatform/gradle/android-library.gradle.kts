// Applied only when the Android target is enabled (see core/build.gradle.kts).
// Isolating AGP DSL here keeps the main build script compilable without AGP on
// the classpath. The version catalog `libs` accessor is not generated for applied
// scripts, so SDK levels are kept in sync with gradle/libs.versions.toml by hand.
import com.android.build.gradle.LibraryExtension

extensions.configure<LibraryExtension>("android") {
    namespace = (extra["v2ray.android.namespace"] as? String) ?: "com.v2ray.compose.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

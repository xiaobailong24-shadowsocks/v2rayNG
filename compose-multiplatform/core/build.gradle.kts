import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// ---- Target detection -------------------------------------------------------
// Android and iOS targets are enabled lazily so this module still configures,
// compiles and tests on a plain JVM host (no Android SDK, non-macOS) — the
// environment used for automated verification. No Android Gradle Plugin type is
// referenced from this script; AGP config lives in gradle/android-library.gradle.kts
// which is only applied (and therefore only compiled) when Android is enabled.
val androidEnabled = resolveTargetFlag("enableAndroid") {
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        rootProject.file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
}
val iosEnabled = resolveTargetFlag("enableIos") {
    System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }
}

if (androidEnabled) {
    pluginManager.apply("com.android.library")
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    jvm()

    if (androidEnabled) {
        androidTarget {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                }
            }
        }
    }

    if (iosEnabled) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
        if (androidEnabled) {
            named("androidMain").configure {
                dependencies { implementation(libs.kotlinx.coroutines.android) }
            }
        }
    }
}

if (androidEnabled) {
    extra["v2ray.android.namespace"] = "com.v2ray.compose.core"
    apply(from = rootProject.file("gradle/android-library.gradle.kts"))
}

fun resolveTargetFlag(prop: String, auto: () -> Boolean): Boolean =
    when (providers.gradleProperty(prop).getOrElse("auto")) {
        "true" -> true
        "false" -> false
        else -> auto()
    }

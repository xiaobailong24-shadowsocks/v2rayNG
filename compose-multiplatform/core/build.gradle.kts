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
        // Apple x86_64 (iosX64/macosX64) was removed in Compose Multiplatform 1.11 /
        // recent Kotlin; device + Apple-silicon simulator cover all supported hosts.
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

// Configure the Android Library extension dynamically so this script compiles
// without AGP on the classpath (the JVM-only verify/sandbox path). withGroovyBuilder
// names no com.android.* type at compile time; see composeApp/build.gradle.kts.
if (androidEnabled) {
    extensions.getByName("android").withGroovyBuilder {
        setProperty("namespace", "com.v2ray.compose.core")
        setProperty("compileSdk", 35)
        "defaultConfig" {
            setProperty("minSdk", 24)
        }
        "compileOptions" {
            setProperty("sourceCompatibility", JavaVersion.VERSION_17)
            setProperty("targetCompatibility", JavaVersion.VERSION_17)
        }
    }
}

fun resolveTargetFlag(prop: String, auto: () -> Boolean): Boolean =
    when (providers.gradleProperty(prop).getOrElse("auto")) {
        "true" -> true
        "false" -> false
        else -> auto()
    }

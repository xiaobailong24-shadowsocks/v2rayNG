import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val androidEnabled = resolveTargetFlag("enableAndroid") {
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        rootProject.file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
}
val iosEnabled = resolveTargetFlag("enableIos") {
    System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }
}

if (androidEnabled) {
    pluginManager.apply("com.android.application")
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    jvm("desktop")

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
        // Apple x86_64 (iosX64) was removed in Compose Multiplatform 1.11; ship the
        // ARM device slice plus the Apple-silicon simulator slice.
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
            // tun2socks (hev-socks5-tunnel) bound via cinterop and driven from
            // iosMain Kotlin — no Swift bridging header. See NATIVE.md.
            target.compilations.getByName("main").cinterops.create("hev") {
                defFile(project.file("src/nativeInterop/cinterop/hev.def"))
                packageName("hev")
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            // Direct dependencies instead of the deprecated compose.* DSL aliases.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        if (androidEnabled) {
            val withNative = providers.gradleProperty("withNative").getOrElse("false") == "true"
            named("androidMain").configure {
                dependencies {
                    implementation("androidx.activity:activity-compose:1.9.3")
                    implementation("androidx.core:core-ktx:1.13.1")
                    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                    implementation(libs.kotlinx.coroutines.android)
                    // Xray-core: prebuilt libv2ray.aar (gomobile build of
                    // AndroidLibXrayLite). Absent = empty, UI still builds. See NATIVE.md.
                    implementation(project.fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
                }
                // The libv2ray-dependent core wrapper is only compiled for full
                // (native) builds; without it XrayCore.load() returns null.
                if (withNative) kotlin.srcDir("src/androidNative/kotlin")
            }
        }
    }
}

// On a desktop/JVM-only build the Compose Multiplatform lifecycle libraries are
// provided by the `org.jetbrains.androidx.lifecycle` artifacts (Maven Central);
// the real AndroidX `androidx.lifecycle` / `androidx.arch.core` transitives are
// only needed by the Android target. Excluding them when Android is disabled lets
// the desktop build resolve without Google's Maven repository.
if (!androidEnabled) {
    configurations.configureEach {
        exclude(group = "androidx.lifecycle")
        exclude(group = "androidx.arch.core")
    }
}

compose.desktop {
    application {
        mainClass = "com.v2ray.compose.ui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "V2rayNGCompose"
            packageVersion = "1.0.0"
        }
    }
}

// Configure the (already-applied) Android Application extension *dynamically*.
// A direct `com.android.build.api.dsl.*` reference — inline or via apply(from=) —
// forces this script to compile against AGP, which the desktop / verify / sandbox
// path deliberately keeps off the classpath (no Google Maven, no SDK). An applied
// script can't resolve AGP either (it doesn't inherit the buildscript classpath).
// withGroovyBuilder names no com.android.* type at compile time and configures the
// real extension at runtime, only when androidEnabled and the plugin is present.
// (Manifest/res/jniLibs default to src/androidMain/… for a KMP androidTarget.)
if (androidEnabled) {
    extensions.getByName("android").withGroovyBuilder {
        setProperty("namespace", "com.v2ray.compose")
        setProperty("compileSdk", 35)
        "defaultConfig" {
            setProperty("applicationId", "com.v2ray.compose")
            setProperty("minSdk", 24)
            setProperty("targetSdk", 35)
            setProperty("versionCode", 1)
            setProperty("versionName", "1.0.0")
        }
        "compileOptions" {
            setProperty("sourceCompatibility", JavaVersion.VERSION_17)
            setProperty("targetCompatibility", JavaVersion.VERSION_17)
        }
        "buildFeatures" {
            setProperty("compose", true)
        }
        "packaging" {
            "resources" {
                @Suppress("UNCHECKED_CAST")
                (getProperty("excludes") as MutableSet<String>).add("/META-INF/{AL2.0,LGPL2.1}")
            }
        }
    }
}

// Headless offscreen render of the shared UI for visual verification.
tasks.register<JavaExec>("screenshot") {
    dependsOn("desktopMainClasses")
    val comp = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = comp.output.allOutputs + (comp.runtimeDependencyFiles ?: files())
    mainClass.set("com.v2ray.compose.ui.ScreenshotKt")
}

fun resolveTargetFlag(prop: String, auto: () -> Boolean): Boolean =
    when (providers.gradleProperty(prop).getOrElse("auto")) {
        "true" -> true
        "false" -> false
        else -> auto()
    }

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
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
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
            named("androidMain").configure {
                dependencies {
                    implementation("androidx.activity:activity-compose:1.9.3")
                    implementation("androidx.core:core-ktx:1.13.1")
                    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                    implementation(libs.kotlinx.coroutines.android)
                }
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

if (androidEnabled) {
    extra["v2ray.android.namespace"] = "com.v2ray.compose"
    apply(from = rootProject.file("gradle/android-application.gradle.kts"))
}

// Headless offscreen render of the shared UI for visual verification.
tasks.register<JavaExec>("screenshot") {
    dependsOn("desktopMainClasses")
    val comp = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = comp.output.allOutputs + comp.runtimeDependencyFiles
    mainClass.set("com.v2ray.compose.ui.ScreenshotKt")
}

fun resolveTargetFlag(prop: String, auto: () -> Boolean): Boolean =
    when (providers.gradleProperty(prop).getOrElse("auto")) {
        "true" -> true
        "false" -> false
        else -> auto()
    }

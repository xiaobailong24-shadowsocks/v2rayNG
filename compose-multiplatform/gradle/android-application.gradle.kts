// Applied only when the Android target is enabled (see composeApp/build.gradle.kts).
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

// Builds libv2ray.so (JNI shim + Xray-core + hev-socks5-tunnel). Opt-in with
// -PwithNative=true once the native prerequisites are in place (run
// cpp/xray/build-android.sh and add the hev-socks5-tunnel submodule — see
// NATIVE.md). UI-only / CI builds omit it; V2RayBridge degrades gracefully when
// libv2ray.so is absent.
val withNative = providers.gradleProperty("withNative").getOrElse("false") == "true"
val cmakeFile = file("src/androidMain/cpp/CMakeLists.txt")

extensions.configure<BaseAppModuleExtension>("android") {
    namespace = (extra["v2ray.android.namespace"] as? String) ?: "com.v2ray.compose"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.v2ray.compose"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        if (withNative && cmakeFile.exists()) {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
            externalNativeBuild {
                cmake { arguments += "-DANDROID_STL=c++_shared" }
            }
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }

    if (withNative && cmakeFile.exists()) {
        externalNativeBuild {
            cmake {
                path = cmakeFile
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

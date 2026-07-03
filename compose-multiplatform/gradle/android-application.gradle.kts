// Applied only when the Android target is enabled (see composeApp/build.gradle.kts).
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

extensions.configure<BaseAppModuleExtension>("android") {
    namespace = (extra["v2ray.android.namespace"] as? String) ?: "com.v2ray.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.v2ray.compose"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // Only ship native ABIs; the prebuilt hev-socks5-tunnel .so and libv2ray.aar
        // provide these. (jniLibs default dir src/androidMain/jniLibs is auto-included.)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
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

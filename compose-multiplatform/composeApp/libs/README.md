# composeApp/libs

Drop the prebuilt Xray-core AAR here:

    libv2ray.aar

It is the gomobile build of [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
— the same Xray-core binary v2rayNG ships. Produce it with
`scripts/build-native-android.sh`, or copy it from a v2rayNG build.

Consumed via `fileTree(...)` in `composeApp/build.gradle.kts`. When absent, the
app still builds; `XrayCore.load()` returns null and connecting reports the core
as unavailable.

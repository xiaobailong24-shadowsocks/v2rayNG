package com.v2ray.compose.ui

import com.v2ray.compose.core.presentation.AppViewModel

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.v2ray.compose.core.config.ConfigParser
import com.v2ray.compose.core.repository.InMemoryProfileRepository
import com.v2ray.compose.core.util.Base64Util
import com.v2ray.compose.core.vpn.NoopVpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Offscreen render of [App] to a PNG. Used for headless visual verification of
 * the shared Compose UI (no display server required).
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun main() {
    val repo = InMemoryProfileRepository()
    repo.upsertAll(
        listOfNotNull(
            ConfigParser.parse("vless://11111111-2222-3333-4444-555555555555@tokyo.example.com:443?encryption=none&security=reality&type=grpc&serviceName=grpc&sni=www.microsoft.com&fp=chrome&pbk=KEY&flow=xtls-rprx-vision#🇯🇵 Tokyo Reality"),
            ConfigParser.parse("trojan://password@sg.example.com:8443?type=ws&host=cdn.example.com&path=%2Fpath&security=tls#🇸🇬 Singapore WS"),
            ConfigParser.parse("vmess://" + Base64Util.encodeToString("""{"v":"2","ps":"🇺🇸 US VMess","add":"us.example.com","port":"443","id":"abcd","aid":"0","scy":"auto","net":"ws","type":"none","host":"h","path":"/vm","tls":"tls"}""")),
            ConfigParser.parse("ss://" + Base64Util.encodeUrlSafeNoPadding("aes-256-gcm:secret".encodeToByteArray()) + "@hk.example.com:8388#🇭🇰 Hong Kong SS"),
        )
    )
    repo.select(repo.profiles.value.firstOrNull()?.id)

    val vm = AppViewModel(repo, NoopVpnController(), CoroutineScope(Dispatchers.Unconfined))

    val width = 430
    val height = 780
    val scene = ImageComposeScene(width = width, height = height, density = Density(2f)) {
        App(vm)
    }
    val image = scene.render()
    val bytes = image.encodeToData()?.bytes ?: error("Failed to encode image")
    val out = File(System.getProperty("screenshot.out") ?: "app-preview.png")
    out.writeBytes(bytes)
    scene.close()
    println("Wrote screenshot to ${out.absolutePath} (${bytes.size} bytes)")
}

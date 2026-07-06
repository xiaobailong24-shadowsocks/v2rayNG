package com.v2ray.compose.core

import com.v2ray.compose.core.config.ConfigParser
import com.v2ray.compose.core.config.SubscriptionParser
import com.v2ray.compose.core.config.XrayConfigBuilder
import com.v2ray.compose.core.util.Base64Util
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubscriptionAndBuilderTest {

    @Test
    fun subscription_base64_body() {
        val links = listOf(
            "vless://uuid-a@a.com:443?encryption=none&security=tls&type=tcp#A",
            "trojan://pw@b.com:443#B",
        ).joinToString("\n")
        val body = Base64Util.encodeToString(links)
        val profiles = SubscriptionParser.parse(body, subscriptionId = "sub1")
        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.subscriptionId == "sub1" })
        assertEquals("A", profiles[0].remarks)
        assertEquals("B", profiles[1].remarks)
    }

    @Test
    fun subscription_plaintext_body() {
        val body = "vless://uuid-a@a.com:443?encryption=none&type=tcp#A\n# a comment line\nnot-a-link"
        val profiles = SubscriptionParser.parse(body)
        assertEquals(1, profiles.size)
        assertEquals("a.com", profiles[0].server)
    }

    @Test
    fun subscription_skips_invalid_lines() {
        val body = "garbage\n\nvless://u@h.com:443?type=tcp#X\n"
        assertEquals(1, SubscriptionParser.parse(body).size)
    }

    @Test
    fun xray_config_vless_ws_tls_structure() {
        val p = ConfigParser.parse(
            "vless://uuid-1@srv.com:443?encryption=none&security=tls&type=ws&host=cdn.com&path=%2Fws&sni=srv.com#N"
        )
        assertNotNull(p)
        val config = XrayConfigBuilder.buildConfig(p)

        // inbounds: socks + http
        val inbounds = config["inbounds"]!!.jsonArray
        assertEquals(2, inbounds.size)
        assertEquals("socks", inbounds[0].jsonObject["protocol"]!!.jsonPrimitive.content)

        // outbound[0] is the proxy
        val outbound = config["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("vless", outbound["protocol"]!!.jsonPrimitive.content)

        val vnext = outbound["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject
        assertEquals("srv.com", vnext["address"]!!.jsonPrimitive.content)
        assertEquals(443, vnext["port"]!!.jsonPrimitive.content.toInt())
        val user = vnext["users"]!!.jsonArray[0].jsonObject
        assertEquals("uuid-1", user["id"]!!.jsonPrimitive.content)
        assertEquals("none", user["encryption"]!!.jsonPrimitive.content)

        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
        assertEquals("tls", stream["security"]!!.jsonPrimitive.content)
        assertEquals("srv.com", stream["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content)
        val ws = stream["wsSettings"]!!.jsonObject
        assertEquals("/ws", ws["path"]!!.jsonPrimitive.content)
        assertEquals("cdn.com", ws["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content)
    }

    @Test
    fun xray_config_shadowsocks_structure() {
        val userInfo = Base64Util.encodeUrlSafeNoPadding("aes-256-gcm:pw".encodeToByteArray())
        val p = ConfigParser.parse("ss://$userInfo@ss.com:8388#S")
        assertNotNull(p)
        val outbound = XrayConfigBuilder.buildConfig(p)["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("shadowsocks", outbound["protocol"]!!.jsonPrimitive.content)
        val server = outbound["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("aes-256-gcm", server["method"]!!.jsonPrimitive.content)
        assertEquals("pw", server["password"]!!.jsonPrimitive.content)
        assertEquals("ss.com", server["address"]!!.jsonPrimitive.content)
    }

    @Test
    fun xray_config_is_valid_json_string() {
        val p = ConfigParser.parse("trojan://pw@t.com:443?type=grpc&serviceName=svc#T")
        assertNotNull(p)
        val jsonStr = XrayConfigBuilder.buildConfigJson(p)
        assertTrue(jsonStr.contains("\"protocol\": \"trojan\""))
        assertTrue(jsonStr.contains("\"grpcSettings\""))
    }

    @Test
    fun xray_config_httpupgrade_uses_toplevel_host_not_headers() {
        // Xray rejects a "Host" key inside httpupgradeSettings.headers; the camouflage
        // host must be the dedicated top-level `host` field.
        val p = ConfigParser.parse(
            "vless://uuid-1@srv.com:443?encryption=none&security=tls&type=httpupgrade&host=cdn.com&path=%2Fup#N"
        )
        assertNotNull(p)
        val stream = XrayConfigBuilder.buildConfig(p)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("httpupgrade", stream["network"]!!.jsonPrimitive.content)
        val hu = stream["httpupgradeSettings"]!!.jsonObject
        assertEquals("/up", hu["path"]!!.jsonPrimitive.content)
        assertEquals("cdn.com", hu["host"]!!.jsonPrimitive.content)
        assertTrue(hu["headers"] == null, "httpupgrade must not nest Host under headers")
    }

    @Test
    fun xray_config_tcp_http_header_request_is_object_with_host() {
        // request.headers must be an OBJECT (name -> string list); an empty array
        // fails the whole Xray config parse, and the camouflage Host must be present.
        val p = ConfigParser.parse(
            "vless://uuid-1@srv.com:443?encryption=none&security=none&type=tcp&headerType=http&host=cdn.com&path=%2F#N"
        )
        assertNotNull(p)
        val stream = XrayConfigBuilder.buildConfig(p)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        val header = stream["tcpSettings"]!!.jsonObject["header"]!!.jsonObject
        assertEquals("http", header["type"]!!.jsonPrimitive.content)
        val request = header["request"]!!.jsonObject
        assertEquals("GET", request["method"]!!.jsonPrimitive.content)
        val headers = request["headers"]!!.jsonObject // must be an object, not an array
        val hostList = headers["Host"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("cdn.com"), hostList)
    }
}

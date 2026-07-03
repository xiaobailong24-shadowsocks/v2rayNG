package com.v2ray.compose.core

import com.v2ray.compose.core.config.ConfigParser
import com.v2ray.compose.core.config.ConfigSerializer
import com.v2ray.compose.core.model.EConfigType
import com.v2ray.compose.core.util.Base64Util
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigParserTest {

    @Test
    fun parse_vless_ws_tls() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=ws&host=cdn.example.com&path=%2Fwspath" +
            "&sni=example.com&fp=chrome&flow=xtls-rprx-vision#My%20Node"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals(EConfigType.VLESS, p.configType)
        assertEquals("11111111-2222-3333-4444-555555555555", p.password)
        assertEquals("example.com", p.server)
        assertEquals("443", p.serverPort)
        assertEquals("ws", p.network)
        assertEquals("tls", p.streamSecurity)
        assertEquals("cdn.example.com", p.host)
        assertEquals("/wspath", p.path)
        assertEquals("example.com", p.sni)
        assertEquals("chrome", p.fingerprint)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("My Node", p.remarks)
    }

    @Test
    fun parse_trojan_grpc() {
        val link = "trojan://pass123@t.example.com:8443?type=grpc&serviceName=mygrpc&security=tls#Trojan%20A"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals(EConfigType.TROJAN, p.configType)
        assertEquals("pass123", p.password)
        assertEquals("t.example.com", p.server)
        assertEquals("8443", p.serverPort)
        assertEquals("grpc", p.network)
        assertEquals("mygrpc", p.path)
        assertEquals("tls", p.streamSecurity)
        assertEquals("Trojan A", p.remarks)
    }

    @Test
    fun parse_trojan_defaults_to_tls() {
        val p = ConfigParser.parse("trojan://pw@host.net:443#T")
        assertNotNull(p)
        assertEquals("tls", p.streamSecurity)
    }

    @Test
    fun parse_shadowsocks_sip002() {
        val userInfo = Base64Util.encodeUrlSafeNoPadding("aes-256-gcm:mypassword".encodeToByteArray())
        val link = "ss://$userInfo@ss.example.com:8388#SS%20Node"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals(EConfigType.SHADOWSOCKS, p.configType)
        assertEquals("aes-256-gcm", p.method)
        assertEquals("mypassword", p.password)
        assertEquals("ss.example.com", p.server)
        assertEquals("8388", p.serverPort)
        assertEquals("SS Node", p.remarks)
    }

    @Test
    fun parse_shadowsocks_legacy() {
        val body = Base64Util.encodeToString("chacha20-ietf-poly1305:secret@1.2.3.4:8389")
        val p = ConfigParser.parse("ss://$body#Legacy")
        assertNotNull(p)
        assertEquals("chacha20-ietf-poly1305", p.method)
        assertEquals("secret", p.password)
        assertEquals("1.2.3.4", p.server)
        assertEquals("8389", p.serverPort)
    }

    @Test
    fun parse_vmess_json() {
        val jsonBody = """
            {"v":"2","ps":"VMess Node","add":"v.example.com","port":"443","id":"abcd-uuid",
             "aid":"0","scy":"auto","net":"ws","type":"none","host":"h.example.com",
             "path":"/vm","tls":"tls","sni":"v.example.com","fp":"chrome"}
        """.trimIndent()
        val link = "vmess://" + Base64Util.encodeToString(jsonBody)
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals(EConfigType.VMESS, p.configType)
        assertEquals("VMess Node", p.remarks)
        assertEquals("v.example.com", p.server)
        assertEquals("443", p.serverPort)
        assertEquals("abcd-uuid", p.password)
        assertEquals("ws", p.network)
        assertEquals("h.example.com", p.host)
        assertEquals("/vm", p.path)
        assertEquals("tls", p.streamSecurity)
    }

    @Test
    fun parse_hysteria2() {
        val link = "hysteria2://mypass@hy.example.com:443?sni=hy.example.com&insecure=1&obfs-password=xyz#HY2"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals(EConfigType.HYSTERIA2, p.configType)
        assertEquals("mypass", p.password)
        assertEquals("hy.example.com", p.server)
        assertEquals(true, p.allowInsecure)
        assertEquals("xyz", p.obfsPassword)
    }

    @Test
    fun parse_ipv6_host() {
        val link = "trojan://pw@[2001:db8::1]:443#v6"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        assertEquals("2001:db8::1", p.server)
        assertEquals("443", p.serverPort)
    }

    @Test
    fun unknown_scheme_returns_null() {
        assertNull(ConfigParser.parse("https://example.com"))
        assertNull(ConfigParser.parse("not a link"))
        assertNull(ConfigParser.parse(""))
    }

    @Test
    fun roundtrip_vless_through_serializer() {
        val link = "vless://uuid-1@h.example.com:443?encryption=none&security=reality&type=grpc" +
            "&serviceName=svc&sni=microsoft.com&fp=chrome&pbk=PUBKEY&sid=ab12#Reality"
        val p = ConfigParser.parse(link)
        assertNotNull(p)
        val reparsed = ConfigParser.parse(ConfigSerializer.toUri(p))
        assertNotNull(reparsed)
        assertEquals(p.password, reparsed.password)
        assertEquals(p.server, reparsed.server)
        assertEquals(p.serverPort, reparsed.serverPort)
        assertEquals(p.network, reparsed.network)
        assertEquals(p.streamSecurity, reparsed.streamSecurity)
        assertEquals(p.publicKey, reparsed.publicKey)
        assertEquals(p.shortId, reparsed.shortId)
        assertEquals(p.remarks, reparsed.remarks)
    }

    @Test
    fun roundtrip_vmess_through_serializer() {
        val jsonBody = """{"v":"2","ps":"N","add":"a.com","port":"443","id":"id1","aid":"0",
            "scy":"auto","net":"ws","type":"none","host":"h","path":"/p","tls":"tls"}"""
        val p = ConfigParser.parse("vmess://" + Base64Util.encodeToString(jsonBody))
        assertNotNull(p)
        val reparsed = ConfigParser.parse(ConfigSerializer.toUri(p))
        assertNotNull(reparsed)
        assertEquals("a.com", reparsed.server)
        assertEquals("id1", reparsed.password)
        assertEquals("ws", reparsed.network)
        assertEquals("/p", reparsed.path)
        assertTrue(reparsed.streamSecurity == "tls")
    }
}

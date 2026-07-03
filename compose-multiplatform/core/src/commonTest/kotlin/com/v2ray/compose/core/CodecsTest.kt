package com.v2ray.compose.core

import com.v2ray.compose.core.util.Base64Util
import com.v2ray.compose.core.util.UrlCodec
import com.v2ray.compose.core.util.parseQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodecsTest {

    @Test
    fun base64_roundtrip_standard() {
        val text = "hello:world@host:443"
        val encoded = Base64Util.encodeToString(text)
        assertEquals(text, Base64Util.decodeToString(encoded))
    }

    @Test
    fun base64_decodes_without_padding() {
        // "any carnal pleasure" without '=' padding
        assertEquals("any carnal pleasure", Base64Util.decodeToString("YW55IGNhcm5hbCBwbGVhc3VyZQ"))
    }

    @Test
    fun base64_decodes_urlsafe_alphabet() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val urlSafe = Base64Util.encodeUrlSafeNoPadding(bytes)
        // url-safe uses '-' and '_' instead of '+' and '/'
        assertEquals(bytes.toList(), Base64Util.decode(urlSafe)?.toList())
    }

    @Test
    fun base64_invalid_returns_null() {
        assertNull(Base64Util.decodeToString(""))
    }

    @Test
    fun url_decode_percent_and_plus() {
        assertEquals("/path with space", UrlCodec.decode("%2Fpath+with%20space"))
    }

    @Test
    fun url_encode_reserved() {
        assertEquals("%2Fa%20b", UrlCodec.encode("/a b"))
    }

    @Test
    fun query_parsing() {
        val q = parseQuery("type=ws&security=tls&path=%2Fws&empty=")
        assertEquals("ws", q["type"])
        assertEquals("tls", q["security"])
        assertEquals("/ws", q["path"])
        assertEquals("", q["empty"])
    }
}

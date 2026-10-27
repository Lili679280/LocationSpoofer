package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WigleClientTest {

    private val encoded = java.util.Base64.getEncoder().encodeToString("AIDabc123:0123456789abcdef".toByteArray())

    @Test
    fun `accepts the encoded value from the account page`() {
        assertEquals(encoded, WigleClient.authToken("  $encoded "))
    }

    @Test
    fun `rejects an unencoded name and token`() {
        assertNull(WigleClient.authToken("AIDabc123:0123456789abcdef"))
    }

    @Test
    fun `rejects a bare api token and empty input`() {
        assertNull(WigleClient.authToken("0123456789abcdef0123456789abcdef"))
        assertNull(WigleClient.authToken("   "))
    }
}

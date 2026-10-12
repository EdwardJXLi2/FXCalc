package dev.hydranet.fxcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    @Test
    fun acceptsSharedNumbersAndTrimsWhitespace() {
        listOf("25", "25.5", "25,5", ".5", "0", "123456789012.123456").forEach {
            assertEquals(it, parseSharedAmount("  $it\n"))
        }
    }

    @Test
    fun rejectsMissingMalformedAndUnsupportedAmounts() {
        listOf(
            null, "", " ", ".", ",", "-5", "25 USD", "1.2.3", "1,000.50",
            "1234567890123", "1.1234567", "https://example.com/25",
        ).forEach {
            assertNull(it, parseSharedAmount(it))
        }
    }
}

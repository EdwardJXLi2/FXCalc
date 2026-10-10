package dev.hydranet.fxcalc

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickConvertInputTest {
    @Test
    fun parsesAmountWithOptionalCurrencyCode() {
        assertEquals(QuickConvertRequest(BigDecimal("25"), null), parseQuickConvertInput(" 25 "))
        assertEquals(QuickConvertRequest(BigDecimal("25.5"), "EUR"), parseQuickConvertInput("25.5 eur"))
        assertEquals(QuickConvertRequest(BigDecimal("25.5"), "EUR"), parseQuickConvertInput("EUR25,5"))
    }

    @Test
    fun rejectsTextThatIsNotASingleAmount() {
        listOf("", "abc", "EUR", "25 EUR USD", "EUR 25 USD", "1.2.3", "-5").forEach {
            assertNull(it, parseQuickConvertInput(it))
        }
    }
}

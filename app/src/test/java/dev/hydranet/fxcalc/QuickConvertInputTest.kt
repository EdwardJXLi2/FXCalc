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
        assertEquals(QuickConvertRequest(BigDecimal("25"), "USD"), parseQuickConvertInput("25 USD"))
    }

    @Test
    fun parsesExplicitTargetWithOptionalSourceCurrency() {
        assertEquals(
            QuickConvertRequest(BigDecimal("25"), "USD", "CAD"),
            parseQuickConvertInput("25 USD to CAD"),
        )
        assertEquals(
            QuickConvertRequest(BigDecimal("25"), null, "CAD"),
            parseQuickConvertInput("25 to CAD"),
        )
        assertEquals(
            QuickConvertRequest(BigDecimal("25.5"), "USD", "CAD"),
            parseQuickConvertInput("  usd 25,5  To  cad  "),
        )
        assertEquals(
            QuickConvertRequest(BigDecimal("0"), "CAD", "CAD"),
            parseQuickConvertInput("0 CAD to CAD"),
        )
    }

    @Test
    fun rejectsTextThatIsNotASingleAmount() {
        listOf(
            "", "abc", "EUR", "25 EUR USD", "EUR 25 USD", "1.2.3", "-5",
            "USD to CAD", "to CAD", "25 to", "25 to CA", "25 to CADD",
            "25 USD to CAD to EUR", "USD 25 EUR to CAD", "25 toCAD",
        ).forEach {
            assertNull(it, parseQuickConvertInput(it))
        }
    }
}

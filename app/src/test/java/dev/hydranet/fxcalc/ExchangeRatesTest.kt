package dev.hydranet.fxcalc

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ExchangeRatesTest {
    @Test
    fun parsesEcbRatesAndConvertsBetweenNonEuroCurrencies() {
        val rates = EcbRatesParser.parse(
            """
            <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
                xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
                <Cube><Cube time="2026-09-18">
                    <Cube currency="USD" rate="1.25"/>
                    <Cube currency="CHF" rate="1.00"/>
                </Cube></Cube>
            </gesmes:Envelope>
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 9, 18), rates.date)
        assertEquals(0, BigDecimal("80").compareTo(rates.convert(BigDecimal("100"), "USD", "CHF")))
        assertEquals(0, BigDecimal("125").compareTo(rates.convert(BigDecimal("100"), "EUR", "USD")))
        assertNull(rates.convert(BigDecimal.ONE, "USD", "CAD"))
    }

    @Test
    fun rejectsFeedsWithoutDatedRates() {
        assertThrows(IllegalArgumentException::class.java) {
            EcbRatesParser.parse("<Cube/>")
        }
    }

    @Test
    fun rejectsDtds() {
        assertThrows(IllegalArgumentException::class.java) {
            EcbRatesParser.parse("<!DOCTYPE Cube [<!ENTITY example 'value'>]><Cube/>")
        }
    }

    @Test
    fun parsesExchangeRateApiUsdRates() {
        val rates = ExchangeRateApiParser.parse(
            """
            {
                "result": "success",
                "time_last_update_unix": 1789689600,
                "base_code": "USD",
                "rates": { "USD": 1, "EUR": 0.8, "CHF": 1.0 }
            }
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 9, 18), rates.date)
        assertEquals(0, BigDecimal("80").compareTo(rates.convert(BigDecimal("100"), "USD", "EUR")))
        assertEquals(0, BigDecimal("125").compareTo(rates.convert(BigDecimal("100"), "EUR", "CHF")))
        assertNull(rates.convert(BigDecimal.ONE, "USD", "CAD"))
    }

    @Test
    fun rejectsFailedExchangeRateApiResponses() {
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeRateApiParser.parse("""{"result":"error","error-type":"unsupported-code"}""")
        }
    }

    @Test
    fun parsesFrankfurterRates() {
        val rates = FrankfurterRatesParser.parse(
            """
            [
                {"date":"2026-09-18","base":"USD","quote":"EUR","rate":0.8},
                {"date":"2026-09-17","base":"USD","quote":"CHF","rate":0.8}
            ]
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 9, 17), rates.date)
        assertEquals(0, BigDecimal("80").compareTo(rates.convert(BigDecimal("100"), "USD", "CHF")))
    }

    @Test
    fun parsesFloatRates() {
        val rates = FloatRatesParser.parse(
            """
            {
                "eur": {
                    "code": "EUR",
                    "rate": "0.8",
                    "date": "Fri, 18 Sep 2026 20:55:06 GMT"
                },
                "chf": {
                    "code": "CHF",
                    "rate": "0.8",
                    "date": "Fri, 18 Sep 2026 20:55:06 GMT"
                }
            }
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 9, 18), rates.date)
        assertEquals(0, BigDecimal("80").compareTo(rates.convert(BigDecimal("100"), "USD", "CHF")))
    }

    @Test
    fun parsesCommunityCurrencyApiRates() {
        val rates = CommunityCurrencyApiParser.parse(
            """
            {
                "date": "2026-09-18",
                "usd": {"usd": 1, "eur": 0.8, "chf": 0.8, "1inch": 12.3}
            }
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 9, 18), rates.date)
        assertEquals(0, BigDecimal("80").compareTo(rates.convert(BigDecimal("100"), "USD", "CHF")))
        assertNull(rates.perUsd["1INCH"])
    }

    @Test
    fun usesUsdAsTheDefaultBaseCurrency() {
        assertEquals("USD", CurrencyCatalog.defaultBase())
    }
}

package dev.hydranet.fxcalc

import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

data class ExchangeRates(
    val date: LocalDate,
    val perUsd: Map<String, BigDecimal>,
    val retrievedAt: Instant? = null,
) {
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        val fromRate = perUsd[from] ?: return null
        val toRate = perUsd[to] ?: return null
        return amount.multiply(toRate).divide(fromRate, 12, RoundingMode.HALF_UP)
    }
}

object EcbRatesParser {
    fun parse(xml: String): ExchangeRates {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "ECB feed must not contain a DTD" }
        val rates = mutableMapOf("EUR" to BigDecimal.ONE)
        var date: LocalDate? = null
        val factory = SAXParserFactory.newInstance().apply {
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val handler = object : DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes,
            ) {
                if (qName != "Cube") return
                attributes.getValue("time")?.let { date = LocalDate.parse(it) }
                val code = attributes.getValue("currency") ?: return
                val rate = attributes.getValue("rate")?.toBigDecimalOrNull()
                require(code.matches(Regex("[A-Z]{3}")) && rate != null && rate > BigDecimal.ZERO) {
                    "Invalid ECB currency rate"
                }
                rates[code] = rate
            }
        }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        require(date != null && rates.size > 1) { "ECB feed has no dated rates" }
        return ExchangeRates(date!!, rates.normalizeToUsd())
    }
}

object ExchangeRateApiParser {
    fun parse(json: String): ExchangeRates {
        val root = JSONObject(json)
        require(root.optString("result") == "success") { "ExchangeRate-API request failed" }
        val date = Instant.ofEpochSecond(root.getLong("time_last_update_unix"))
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val usdRates = root.getJSONObject("rates")
        require(root.optString("base_code") == "USD") {
            "ExchangeRate-API feed must use USD as its base"
        }
        val rates = mutableMapOf<String, BigDecimal>()
        for (code in usdRates.keys()) {
            require(code.matches(ISO_CURRENCY_CODE)) { "Invalid ExchangeRate-API currency code" }
            val rate = usdRates.optString(code).toBigDecimalOrNull()
            require(rate != null && rate > BigDecimal.ZERO) { "Invalid ExchangeRate-API rate" }
            rates[code] = rate
        }
        require(rates["USD"]?.compareTo(BigDecimal.ONE) == 0 && rates.size > 1) {
            "ExchangeRate-API feed has no USD-based rates"
        }
        return ExchangeRates(date, rates.toMap())
    }
}

object FrankfurterRatesParser {
    fun parse(json: String): ExchangeRates {
        val rows = JSONArray(json)
        val rates = mutableMapOf("USD" to BigDecimal.ONE)
        var oldestDate: LocalDate? = null

        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            require(row.optString("base").equals("USD", ignoreCase = true)) {
                "Frankfurter feed must use USD as its base"
            }
            val code = row.optString("quote").uppercase()
            val rate = row.optString("rate").toBigDecimalOrNull()
            require(code.matches(ISO_CURRENCY_CODE) && rate != null && rate > BigDecimal.ZERO) {
                "Invalid Frankfurter currency rate"
            }
            rates[code] = rate

            val date = LocalDate.parse(row.getString("date"))
            oldestDate = oldestDate?.let { minOf(it, date) } ?: date
        }

        require(oldestDate != null && rates.size > 1) { "Frankfurter feed has no dated rates" }
        return ExchangeRates(oldestDate, rates.toMap())
    }
}

object FloatRatesParser {
    fun parse(json: String): ExchangeRates {
        val root = JSONObject(json)
        val rates = mutableMapOf("USD" to BigDecimal.ONE)
        var oldestDate: LocalDate? = null

        for (key in root.keys()) {
            val entry = root.getJSONObject(key)
            val code = entry.optString("code").uppercase()
            val rate = entry.optString("rate").toBigDecimalOrNull()
            require(code.matches(ISO_CURRENCY_CODE) && rate != null && rate > BigDecimal.ZERO) {
                "Invalid FloatRates currency rate"
            }
            rates[code] = rate

            val date = ZonedDateTime.parse(
                entry.getString("date"),
                DateTimeFormatter.RFC_1123_DATE_TIME,
            ).toLocalDate()
            oldestDate = oldestDate?.let { minOf(it, date) } ?: date
        }

        require(oldestDate != null && rates.size > 1) { "FloatRates feed has no dated rates" }
        return ExchangeRates(oldestDate, rates.toMap())
    }
}

object CommunityCurrencyApiParser {
    fun parse(json: String): ExchangeRates {
        val root = JSONObject(json)
        val date = LocalDate.parse(root.getString("date"))
        val usdRates = root.getJSONObject("usd")
        val rates = mutableMapOf("USD" to BigDecimal.ONE)

        for (key in usdRates.keys()) {
            val code = key.uppercase()
            if (!code.matches(ISO_CURRENCY_CODE)) continue
            val rate = usdRates.optString(key).toBigDecimalOrNull()
            require(rate != null && rate > BigDecimal.ZERO) {
                "Invalid community currency rate"
            }
            rates[code] = rate
        }

        require(rates.size > 1) { "Community Currency API feed has no rates" }
        return ExchangeRates(date, rates.toMap())
    }
}

private val ISO_CURRENCY_CODE = Regex("[A-Z]{3}")

private fun Map<String, BigDecimal>.normalizeToUsd(): Map<String, BigDecimal> {
    val usdRate = get("USD")
    require(usdRate != null && usdRate > BigDecimal.ZERO) { "Feed has no USD rate" }
    return mapValues { (_, rate) ->
        rate.divide(usdRate, 18, RoundingMode.HALF_UP)
    }.toMutableMap().apply {
        this["USD"] = BigDecimal.ONE
    }
}

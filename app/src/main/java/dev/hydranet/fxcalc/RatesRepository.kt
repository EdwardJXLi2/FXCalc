package dev.hydranet.fxcalc

import android.content.Context
import androidx.core.content.edit
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

class RatesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("fxcalc", Context.MODE_PRIVATE)

    fun cached(source: RateSource = source()): ExchangeRates? =
        preferences.getString(cacheKey(source), null)?.let { body ->
            runCatching {
                parse(source, body).copy(retrievedAt = cachedRetrievedAt(source))
            }.getOrNull()
        }

    fun fetch(source: RateSource = source()): ExchangeRates {
        var lastFailure: Exception? = null
        source.urls.forEach { url ->
            try {
                val body = download(url)
                val retrievedAt = Instant.now()
                val rates = parse(source, body).copy(retrievedAt = retrievedAt)
                preferences.edit {
                    putString(cacheKey(source), body)
                    putLong(retrievedAtKey(source), retrievedAt.toEpochMilli())
                    putBoolean(refreshFailedKey(source), false)
                }
                dataChanged()
                return rates
            } catch (exception: Exception) {
                lastFailure = exception
            }
        }
        preferences.edit { putBoolean(refreshFailedKey(source), true) }
        dataChanged()
        throw lastFailure ?: IllegalStateException("No URL configured for ${source.title}")
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun source(): RateSource = RateSource.fromId(preferences.getString(KEY_SOURCE, null))

    fun saveSource(source: RateSource) {
        preferences.edit { putString(KEY_SOURCE, source.id) }
        dataChanged()
    }

    fun refreshFailed(source: RateSource = source()): Boolean =
        preferences.getBoolean(refreshFailedKey(source), false)

    fun baseCurrency(): String = preferences.getString(KEY_BASE, null)
        ?.takeIf { CurrencyCatalog.find(it) != null }
        ?: CurrencyCatalog.defaultBase()

    fun targetCurrencies(base: String): List<String> {
        val saved = preferences.getString(KEY_TARGETS, null)
        return if (saved == null) {
            CurrencyCatalog.defaultTargets(base)
        } else {
            saved.split(',').filter { it != base && CurrencyCatalog.find(it) != null }.distinct()
        }
    }

    fun saveCurrencies(base: String, targets: List<String>) {
        preferences.edit {
            putString(KEY_BASE, base)
            putString(KEY_TARGETS, targets.joinToString(","))
        }
        dataChanged()
    }

    fun quickConvertEnabled(): Boolean = preferences.getBoolean(KEY_QUICK_CONVERT_ENABLED, false)

    fun saveQuickConvertEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_QUICK_CONVERT_ENABLED, enabled) }
    }

    fun quickConvertInput(): String? = preferences.getString(KEY_QUICK_CONVERT_INPUT, null)

    fun saveQuickConvertInput(input: String) {
        preferences.edit { putString(KEY_QUICK_CONVERT_INPUT, input) }
    }

    private fun dataChanged() {
        ExchangeRatesWidget.notifyDataChanged(appContext)
        QuickConvertNotification.update(appContext)
    }

    private fun parse(source: RateSource, body: String): ExchangeRates = when (source) {
        RateSource.ECB -> EcbRatesParser.parse(body)
        RateSource.FRANKFURTER -> FrankfurterRatesParser.parse(body)
        RateSource.EXCHANGE_RATE_API -> ExchangeRateApiParser.parse(body)
        RateSource.FLOAT_RATES -> FloatRatesParser.parse(body)
        RateSource.COMMUNITY_CURRENCY_API -> CommunityCurrencyApiParser.parse(body)
    }

    private fun cacheKey(source: RateSource) = "$KEY_CACHE_PREFIX${source.id}"

    private fun retrievedAtKey(source: RateSource) = "$KEY_RETRIEVED_AT_PREFIX${source.id}"

    private fun refreshFailedKey(source: RateSource) = "rates_refresh_failed_${source.id}"

    private fun cachedRetrievedAt(source: RateSource): Instant? =
        preferences.getLong(retrievedAtKey(source), 0L)
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)

    companion object {
        private const val KEY_SOURCE = "rate_source"
        private const val KEY_CACHE_PREFIX = "cached_usd_rates_"
        private const val KEY_RETRIEVED_AT_PREFIX = "rates_retrieved_at_"
        private const val KEY_BASE = "base_currency"
        private const val KEY_TARGETS = "target_currencies"
        private const val KEY_QUICK_CONVERT_ENABLED = "quick_convert_enabled"
        private const val KEY_QUICK_CONVERT_INPUT = "quick_convert_input"
    }
}

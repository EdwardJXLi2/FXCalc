package dev.hydranet.fxcalc

import androidx.annotation.StringRes

enum class RateSource(
    val id: String,
    val title: String,
    @get:StringRes val summaryRes: Int,
    val attribution: String,
    val url: String,
    val fallbackUrl: String? = null,
) {
    EXCHANGE_RATE_API(
        id = "er-api",
        title = "ExchangeRate-API",
        summaryRes = R.string.daily_rates,
        attribution = "ExchangeRate-API, open.er-api.com",
        url = "https://open.er-api.com/v6/latest/USD",
    ),
    FRANKFURTER(
        id = "frankfurter",
        title = "Frankfurter",
        summaryRes = R.string.central_bank_rates,
        attribution = "Frankfurter, api.frankfurter.dev",
        url = "https://api.frankfurter.dev/v2/rates?base=usd",
    ),
    FLOAT_RATES(
        id = "float-rates",
        title = "FloatRates",
        summaryRes = R.string.twelve_hour_rates,
        attribution = "FloatRates, floatrates.com",
        url = "https://www.floatrates.com/daily/usd.json",
    ),
    COMMUNITY_CURRENCY_API(
        id = "community-currency-api",
        title = "Currency API",
        summaryRes = R.string.community_daily_rates,
        attribution = "fawazahmed0 Currency API, served by jsDelivr",
        url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
        fallbackUrl = "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json",
    ),
    ECB(
        id = "ecb",
        title = "European Central Bank",
        summaryRes = R.string.euro_reference_rates,
        attribution = "European Central Bank, ecb.europa.eu",
        url = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",
    );

    val urls: List<String>
        get() = listOfNotNull(url, fallbackUrl)

    companion object {
        val default = EXCHANGE_RATE_API

        fun fromId(id: String?): RateSource = entries.firstOrNull { it.id == id } ?: default
    }
}

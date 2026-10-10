package dev.hydranet.fxcalc

data class CurrencyInfo(val code: String, val country: String, val name: String)

object CurrencyCatalog {
    val all = listOf(
        CurrencyInfo("AUD", "Australia", "Australian dollar"),
        CurrencyInfo("BRL", "Brazil", "Brazilian real"),
        CurrencyInfo("CAD", "Canada", "Canadian dollar"),
        CurrencyInfo("CHF", "Switzerland", "Swiss franc"),
        CurrencyInfo("CNY", "China", "Chinese yuan"),
        CurrencyInfo("CZK", "Czechia", "Czech koruna"),
        CurrencyInfo("DKK", "Denmark", "Danish krone"),
        CurrencyInfo("EUR", "Euro area", "Euro"),
        CurrencyInfo("GBP", "United Kingdom", "Pound sterling"),
        CurrencyInfo("HKD", "Hong Kong", "Hong Kong dollar"),
        CurrencyInfo("HUF", "Hungary", "Hungarian forint"),
        CurrencyInfo("IDR", "Indonesia", "Indonesian rupiah"),
        CurrencyInfo("ILS", "Israel", "Israeli shekel"),
        CurrencyInfo("INR", "India", "Indian rupee"),
        CurrencyInfo("ISK", "Iceland", "Icelandic króna"),
        CurrencyInfo("JPY", "Japan", "Japanese yen"),
        CurrencyInfo("KRW", "South Korea", "South Korean won"),
        CurrencyInfo("MXN", "Mexico", "Mexican peso"),
        CurrencyInfo("MYR", "Malaysia", "Malaysian ringgit"),
        CurrencyInfo("NOK", "Norway", "Norwegian krone"),
        CurrencyInfo("NZD", "New Zealand", "New Zealand dollar"),
        CurrencyInfo("PHP", "Philippines", "Philippine peso"),
        CurrencyInfo("PLN", "Poland", "Polish złoty"),
        CurrencyInfo("RON", "Romania", "Romanian leu"),
        CurrencyInfo("SEK", "Sweden", "Swedish krona"),
        CurrencyInfo("SGD", "Singapore", "Singapore dollar"),
        CurrencyInfo("THB", "Thailand", "Thai baht"),
        CurrencyInfo("TRY", "Türkiye", "Turkish lira"),
        CurrencyInfo("USD", "United States", "US dollar"),
        CurrencyInfo("ZAR", "South Africa", "South African rand"),
    )

    private val byCode = all.associateBy { it.code }

    fun find(code: String): CurrencyInfo? = byCode[code]

    fun supported(rates: ExchangeRates?): List<CurrencyInfo> =
        if (rates == null) all else all.filter { it.code in rates.perUsd }

    fun defaultBase(): String = "USD"

    fun defaultTargets(base: String): List<String> =
        listOf("EUR", "USD", "GBP", "CHF", "JPY")
            .filter { it != base }
            .take(4)
}

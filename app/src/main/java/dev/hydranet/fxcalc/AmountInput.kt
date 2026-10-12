package dev.hydranet.fxcalc

internal fun isAmountInput(text: String): Boolean = AMOUNT_PATTERN.matches(text)

internal fun parseSharedAmount(text: String?): String? {
    val amount = text?.trim() ?: return null
    return amount.takeIf {
        isAmountInput(it) && it.replace(',', '.').toBigDecimalOrNull() != null
    }
}

private val AMOUNT_PATTERN = Regex("""\d{0,12}([.,]\d{0,6})?""")

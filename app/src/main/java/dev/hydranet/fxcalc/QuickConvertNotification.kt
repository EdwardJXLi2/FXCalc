package dev.hydranet.fxcalc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import java.math.BigDecimal

internal data class QuickConvertRequest(val amount: BigDecimal, val currency: String?)

/** Parses `25`, `25.5 EUR`, or `EUR 25,5`. Returns null for anything else. */
internal fun parseQuickConvertInput(text: String): QuickConvertRequest? {
    val match = QUICK_CONVERT_PATTERN.matchEntire(text.trim()) ?: return null
    val (prefix, number, suffix) = match.destructured
    if (prefix.isNotEmpty() && suffix.isNotEmpty()) return null
    val amount = number.replace(',', '.').toBigDecimalOrNull() ?: return null
    return QuickConvertRequest(amount, prefix.ifEmpty { suffix }.uppercase().ifEmpty { null })
}

private val QUICK_CONVERT_PATTERN =
    Regex("""(?i)([a-z]{3})?\s*(\d{1,12}(?:[.,]\d{0,6})?)\s*([a-z]{3})?""")

internal object QuickConvertNotification {
    private const val CHANNEL_ID = "quick_convert"
    private const val NOTIFICATION_ID = 1
    internal const val KEY_AMOUNT = "amount"
    internal const val ACTION_CONVERT = "dev.hydranet.fxcalc.QUICK_CONVERT"

    fun enable(context: Context) {
        RatesRepository(context).saveQuickConvertEnabled(true)
        WidgetRefreshService.schedulePeriodic(context)
        update(context)
    }

    fun disable(context: Context) {
        RatesRepository(context).saveQuickConvertEnabled(false)
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun update(context: Context) {
        val repository = RatesRepository(context)
        if (!repository.quickConvertEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.quick_convert),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.quick_convert_channel_description) },
        )
        manager.notify(NOTIFICATION_ID, build(context, repository))
    }

    private fun build(context: Context, repository: RatesRepository): Notification {
        val source = repository.source()
        val rates = repository.cached(source)
        val base = repository.baseCurrency()
        val targets = repository.targetCurrencies(base)
        val exampleCode = targets.firstOrNull() ?: if (base == "USD") "EUR" else "USD"
        val input = repository.quickConvertInput()
        val request = input?.let(::parseQuickConvertInput)
        val from = request?.currency ?: base
        val example = context.getString(R.string.quick_convert_example, exampleCode)

        val title: String
        val text: String
        var expandedView: RemoteViews? = null
        when {
            input == null -> {
                title = context.getString(R.string.quick_convert)
                text = context.getString(R.string.quick_convert_prompt, base, exampleCode)
            }
            request == null -> {
                title = context.getString(R.string.quick_convert_invalid, input)
                text = example
            }
            CurrencyCatalog.find(from) == null -> {
                title = context.getString(R.string.quick_convert_unknown_currency, from)
                text = example
            }
            else -> {
                // Converts to the first listed currency, or back to the base when entered in that currency.
                val to = targets.firstOrNull()?.takeIf { it != from } ?: base.takeIf { it != from }
                val fromAmount = "${formatAmount(request.amount, from)} $from"
                val converted = to?.let { rates?.convert(request.amount, from, it) }
                val unitRate = to?.let { rates?.convert(BigDecimal.ONE, from, it) }
                if (to == null || converted == null || unitRate == null) {
                    title = fromAmount
                    text = when {
                        to == null -> context.getString(R.string.empty_currencies)
                        rates == null -> context.getString(R.string.quick_convert_rates_unavailable)
                        else -> context.getString(R.string.rates_unavailable)
                    }
                } else {
                    val result = "${formatAmount(converted, to)} $to"
                    val rate = "1 $from = ${formatRate(unitRate)} $to"
                    title = result
                    text = context.getString(R.string.quick_convert_from, fromAmount)
                    expandedView = RemoteViews(context.packageName, R.layout.notification_quick_convert).apply {
                        setTextViewText(
                            R.id.quick_convert_from,
                            context.getString(R.string.quick_convert_amount, fromAmount),
                        )
                        setTextViewText(R.id.quick_convert_result, result)
                        setTextViewText(R.id.quick_convert_rate, rate)
                    }
                }
            }
        }

        val convert = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, QuickConvertReceiver::class.java).setAction(ACTION_CONVERT),
            // RemoteInput results are added to the intent, so it must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification_convert),
            context.getString(R.string.quick_convert_action),
            convert,
        )
            .addRemoteInput(
                RemoteInput.Builder(KEY_AMOUNT)
                    .setLabel(context.getString(R.string.quick_convert_label, base))
                    .build(),
            )
            .setAllowGeneratedReplies(false)
            .build()
        val retrievedAt = rates?.retrievedAt

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_convert)
            .setContentTitle(title)
            .setContentText(text)
            .apply {
                if (expandedView == null) {
                    setStyle(Notification.BigTextStyle().bigText(text))
                } else {
                    setStyle(Notification.DecoratedCustomViewStyle())
                    setCustomBigContentView(expandedView)
                }
            }
            .setSubText(source.title)
            .setShowWhen(retrievedAt != null)
            .apply { if (retrievedAt != null) setWhen(retrievedAt.toEpochMilli()) }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(action)
            .build()
    }
}

class QuickConvertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuickConvertNotification.ACTION_CONVERT -> {
                RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(QuickConvertNotification.KEY_AMOUNT)
                    ?.let { RatesRepository(context).saveQuickConvertInput(it.toString().trim()) }
                // Reposting also clears the inline reply's progress indicator.
                QuickConvertNotification.update(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                QuickConvertNotification.update(context)
        }
    }
}

package dev.hydranet.fxcalc

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExchangeRatesWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAll(context)
        WidgetRefreshService.schedulePeriodic(context)
        WidgetRefreshService.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DATA_CHANGED -> updateAll(context)
            ACTION_REFRESH -> {
                updateAll(context, refreshing = true)
                WidgetRefreshService.refreshNow(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshService.cancel(context)
    }

    companion object {
        private const val ACTION_DATA_CHANGED = "dev.hydranet.fxcalc.WIDGET_DATA_CHANGED"
        private const val ACTION_REFRESH = "dev.hydranet.fxcalc.WIDGET_REFRESH"

        internal fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, ExchangeRatesWidget::class.java),
            )

        internal fun notifyDataChanged(context: Context) {
            if (widgetIds(context).isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, ExchangeRatesWidget::class.java).setAction(ACTION_DATA_CHANGED),
                )
            }
        }

        internal fun updateAll(context: Context, refreshing: Boolean = false) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            AppWidgetManager.getInstance(context).updateAppWidget(ids, createViews(context, refreshing))
        }

        internal fun createViews(context: Context, refreshing: Boolean = false): RemoteViews {
            val repository = RatesRepository(context)
            val source = repository.source()
            val base = repository.baseCurrency()
            val targets = repository.targetCurrencies(base)
            val rates = repository.cached(source)
            val failed = repository.refreshFailed(source)
            val locale = Locale.getDefault()
            val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            val fetchedAt = rates?.retrievedAt?.atZone(ZoneId.systemDefault())?.format(
                DateTimeFormatter.ofPattern("d MMM, $timePattern", locale),
            )
            val rateDate = rates?.date?.format(DateTimeFormatter.ofPattern("d MMM", locale))
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Collection rows need a mutable template so the host can attach their fill-in intent.
            val openRow = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val refresh = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ExchangeRatesWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            targets.forEach { code ->
                val rate = rates?.convert(BigDecimal.ONE, base, code)
                val row = RemoteViews(context.packageName, R.layout.widget_rate_row).apply {
                    setTextViewText(R.id.widget_currency, code)
                    setTextViewText(R.id.widget_rate, rate?.let(::formatRate) ?: "—")
                    setContentDescription(
                        R.id.widget_rate_row,
                        if (rate == null) {
                            context.getString(R.string.widget_rate_unavailable, code)
                        } else {
                            context.getString(R.string.widget_rate_description, base, formatRate(rate), code)
                        },
                    )
                    setOnClickFillInIntent(R.id.widget_rate_row, Intent())
                }
                items.addItem(code.hashCode().toLong(), row)
            }
            return RemoteViews(context.packageName, R.layout.exchange_rates_widget).apply {
                setTextViewText(R.id.widget_base, context.getString(R.string.widget_base, base))
                setTextViewText(R.id.widget_source, context.getString(R.string.widget_source, source.title))
                setTextViewText(
                    R.id.widget_status,
                    when {
                        refreshing -> context.getString(R.string.refreshing_rates)
                        failed && rates != null -> context.getString(R.string.widget_saved_rates, fetchedAt ?: rateDate)
                        failed -> context.getString(R.string.widget_refresh_failed)
                        fetchedAt != null -> context.getString(R.string.widget_last_fetched, fetchedAt)
                        rates != null -> context.getString(R.string.widget_rates_dated, rateDate)
                        else -> context.getString(R.string.rates_unavailable)
                    },
                )
                setTextViewText(R.id.widget_empty, context.getString(R.string.empty_currencies))
                setRemoteAdapter(R.id.widget_rates, items.build())
                setEmptyView(R.id.widget_rates, R.id.widget_empty)
                setPendingIntentTemplate(R.id.widget_rates, openRow)
                setOnClickPendingIntent(R.id.widget_header, openApp)
                setOnClickPendingIntent(R.id.widget_empty, openApp)
                setOnClickPendingIntent(R.id.widget_status, openApp)
                setOnClickPendingIntent(R.id.widget_source, openApp)
                setOnClickPendingIntent(R.id.widget_refresh, refresh)
                setBoolean(R.id.widget_refresh, "setEnabled", !refreshing)
            }
        }
    }
}

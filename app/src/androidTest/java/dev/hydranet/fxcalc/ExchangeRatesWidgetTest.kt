package dev.hydranet.fxcalc

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExchangeRatesWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("fxcalc", Context.MODE_PRIVATE)
    private lateinit var savedPreferences: Map<String, *>

    @Before
    fun saveSettingsAndUseFixedRates() {
        savedPreferences = preferences.all
        preferences.edit(commit = true) {
            clear()
            putString("rate_source", "frankfurter")
            putString("base_currency", "EUR")
            putString("target_currencies", "CHF,USD,JPY")
            putLong("rates_retrieved_at_frankfurter", Instant.parse("2026-09-18T14:30:00Z").toEpochMilli())
            putString(
                "cached_usd_rates_frankfurter",
                """[
                    {"date":"2026-09-18","base":"USD","quote":"EUR","rate":0.8},
                    {"date":"2026-09-18","base":"USD","quote":"CHF","rate":0.8}
                ]""",
            )
            putString(
                "cached_usd_rates_er-api",
                """{"result":"success","time_last_update_unix":1789689600,
                    "base_code":"USD","rates":{"USD":1,"EUR":2,"CHF":3}}""",
            )
        }
    }

    @After
    fun restoreSettings() {
        preferences.edit(commit = true) {
            clear()
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
    }

    @Test
    fun usesSavedBaseOrderAndSourceAndShowsUnavailableCurrencies() {
        withWidget { view ->
            assertEquals("1 EUR =", view.text(R.id.widget_base))
            assertEquals("Source: Frankfurter", view.text(R.id.widget_source))
            val list = view.findViewById<ListView>(R.id.widget_rates)
            assertEquals(3, list.adapter.count)
            assertEquals(listOf("CHF", "USD", "JPY"), list.rows().map { it.text(R.id.widget_currency) })
            assertEquals(listOf("1", "1.25", "—"), list.rows().map { it.text(R.id.widget_rate) })
            assertEquals("1 EUR equals 1 CHF", list.rows().first().findViewById<View>(R.id.widget_rate_row).contentDescription)
            val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            val fetchedAt = Instant.parse("2026-09-18T14:30:00Z")
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM, $timePattern", Locale.getDefault()))
            assertEquals("Last fetched: $fetchedAt", view.text(R.id.widget_status))
        }
    }

    @Test
    fun changesInTheAppAreUsedOnTheNextRender() {
        RatesRepository(context).saveCurrencies("USD", listOf("JPY", "CHF", "EUR"))
        RatesRepository(context).saveSource(RateSource.EXCHANGE_RATE_API)
        withWidget { view ->
            assertEquals("1 USD =", view.text(R.id.widget_base))
            assertEquals("Source: ExchangeRate-API", view.text(R.id.widget_source))
            val rateDate = java.time.LocalDate.of(2026, 9, 18)
                .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            assertEquals("Rates dated: $rateDate", view.text(R.id.widget_status))
            val rows = view.findViewById<ListView>(R.id.widget_rates).rows()
            assertEquals(listOf("JPY", "CHF", "EUR"), rows.map { it.text(R.id.widget_currency) })
            assertEquals(listOf("—", "3", "2"), rows.map { it.text(R.id.widget_rate) })
        }
    }

    @Test
    fun failedRefreshKeepsSavedRatesAndTheirDate() {
        preferences.edit(commit = true) { putBoolean("rates_refresh_failed_frankfurter", true) }
        withWidget { view ->
            assertTrue(view.text(R.id.widget_status).startsWith("Saved rates from "))
            assertTrue(view.text(R.id.widget_status).contains("refresh failed"))
            assertEquals("1.25", view.findViewById<ListView>(R.id.widget_rates).rows()[1].text(R.id.widget_rate))
        }
    }

    @Test
    fun noCacheAndNoTargetsHaveUsefulStates() {
        preferences.edit(commit = true) { remove("cached_usd_rates_frankfurter") }
        withWidget { view ->
            assertEquals(context.getString(R.string.rates_unavailable), view.text(R.id.widget_status))
            assertTrue(view.findViewById<ListView>(R.id.widget_rates).rows().all { it.text(R.id.widget_rate) == "—" })
        }
        RatesRepository(context).saveCurrencies("EUR", emptyList())
        withWidget { view ->
            assertEquals(0, view.findViewById<ListView>(R.id.widget_rates).adapter.count)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_empty).visibility)
            assertEquals(context.getString(R.string.empty_currencies), view.text(R.id.widget_empty))
        }
    }

    @Test
    fun refreshingShowsProgressAndDisablesRepeatedTaps() {
        withWidget(refreshing = true) { view ->
            assertEquals(context.getString(R.string.refreshing_rates), view.text(R.id.widget_status))
            assertFalse(view.findViewById<ImageButton>(R.id.widget_refresh).isEnabled)
        }
    }

    @Test
    fun colorsMatchTheAppInLightAndDarkModes() {
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEach { nightMode ->
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val themedContext = context.createConfigurationContext(configuration)
            val colors = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                dynamicDarkColorScheme(themedContext)
            } else {
                dynamicLightColorScheme(themedContext)
            }
            withWidget(themedContext) { view ->
                assertEquals(colors.onPrimaryContainer.toArgb(), view.findViewById<TextView>(R.id.widget_base).currentTextColor)
                assertEquals(colors.primaryContainer.toArgb(), (view.findViewById<View>(R.id.widget_base).background as GradientDrawable).color!!.defaultColor)
                assertEquals(colors.surfaceContainerLow.toArgb(), (view.background as GradientDrawable).color!!.defaultColor)
                val row = view.findViewById<ListView>(R.id.widget_rates).rows().first()
                assertEquals(colors.onSurface.toArgb(), row.findViewById<TextView>(R.id.widget_currency).currentTextColor)
                val rowBackground = row.findViewById<View>(R.id.widget_rate_row).background as GradientDrawable
                assertEquals(colors.surfaceContainerHigh.toArgb(), rowBackground.color!!.defaultColor)
            }
        }
    }

    @Test
    fun minimumSizeHasRoomForAFullCurrencyRow() {
        withWidget { view ->
            val density = context.resources.displayMetrics.density
            view.measure(
                View.MeasureSpec.makeMeasureSpec((180 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.EXACTLY),
            )
            assertTrue(view.findViewById<ListView>(R.id.widget_rates).measuredHeight >= 48 * density)
        }
    }

    private fun withWidget(
        widgetContext: Context = context,
        refreshing: Boolean = false,
        check: (View) -> Unit,
    ) {
        instrumentation.runOnMainSync {
            val host = AppWidgetHostView(widgetContext)
            check(ExchangeRatesWidget.createViews(widgetContext, refreshing).apply(widgetContext, host))
        }
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun ListView.rows(): List<View> = (0 until adapter.count).map { adapter.getView(it, null, this) }
}

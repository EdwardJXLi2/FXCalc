package dev.hydranet.fxcalc

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hydranet.fxcalc.ui.theme.FXCalcTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyReorderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val order = AtomicReference<List<String>>()
    private val refreshCount = AtomicInteger()
    private var refreshing by mutableStateOf(false)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun showConverterWithFixedRates() {
        val initial = listOf("EUR", "GBP", "CHF", "JPY") + CurrencyCatalog.all
            .map { it.code }
            .filter { it !in listOf("EUR", "GBP", "CHF", "JPY", "USD") }
        order.set(initial)
        refreshCount.set(0)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            refreshing = false
            var targets by mutableStateOf(initial)
            val rates = ExchangeRates(
                LocalDate.of(2026, 9, 18),
                CurrencyCatalog.all.associate { it.code to BigDecimal.ONE },
            )
            activity.setContent {
                FXCalcTheme {
                    ConverterScreen(
                        rates = rates,
                        refreshing = refreshing,
                        refreshFailed = false,
                        rateSource = RateSource.default,
                        baseCode = "USD",
                        targetCodes = targets,
                        onRefresh = {
                            refreshCount.incrementAndGet()
                            refreshing = true
                        },
                        onSourceSelected = {},
                        onBaseSelected = {},
                        onTargetAdded = {},
                        onTargetRemoved = {},
                        onTargetMoved = { from, to ->
                            val fromIndex = targets.indexOf(from)
                            val toIndex = targets.indexOf(to)
                            targets = targets.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                            order.set(targets)
                        },
                        onSwap = {},
                    )
                }
            }
        }
        waitUntil { nodeWithDescription(reorderLabel("EUR")) != null }
    }

    @After
    fun closeConverter() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun dragFollowsTheFingerAndCanReverseWithoutOscillating() {
        val first = boundsFor(reorderLabel("EUR"))
        val second = boundsFor(reorderLabel("GBP"))
        val x = first.centerX().toFloat()
        val startY = first.centerY().toFloat()
        val endY = second.centerY() + 24f
        val downTime = beginDrag(x, startY)
        try {
            move(downTime, x, startY, endY)
            waitUntil { order.get().take(2) == listOf("GBP", "EUR") }
            repeat(4) {
                SystemClock.sleep(100)
                assertEquals(listOf("GBP", "EUR"), order.get().take(2))
                assertTrue(
                    "The dragged card must stay under the finger",
                    kotlin.math.abs(boundsFor(reorderLabel("EUR")).centerY() - endY) < 24f,
                )
            }
            move(downTime, x, endY, startY - 24f)
            waitUntil { order.get().take(2) == listOf("EUR", "GBP") }
            repeat(4) {
                SystemClock.sleep(100)
                assertEquals(listOf("EUR", "GBP"), order.get().take(2))
            }
        } finally {
            event(downTime, MotionEvent.ACTION_UP, x, startY - 24f)
        }
        waitUntil(message = { "EUR should settle at $startY, got ${boundsFor(reorderLabel("EUR")).centerY()}" }) {
            kotlin.math.abs(boundsFor(reorderLabel("EUR")).centerY() - startY) < 8f
        }
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun holdingAtEitherEdgeScrollsAndKeepsTheDragActive() {
        val first = boundsFor(reorderLabel("EUR"))
        val metrics = context.resources.displayMetrics
        val x = first.centerX().toFloat()
        val bottom = metrics.heightPixels - 56 * metrics.density
        val downTime = beginDrag(x, first.centerY().toFloat())
        try {
            move(downTime, x, first.centerY().toFloat(), bottom)
            waitUntil(message = { "Edge scrolling stopped at currency ${order.get().indexOf("EUR")}" }) {
                order.get().indexOf("EUR") >= 6
            }
            assertEquals(order.get().size, order.get().distinct().size)
            assertTrue(kotlin.math.abs(boundsFor(reorderLabel("EUR")).centerY() - bottom) < 24f)
            val top = 144 * metrics.density
            move(downTime, x, bottom, top)
            waitUntil(message = { "Top scrolling stopped at currency ${order.get().indexOf("EUR")}" }) {
                order.get().indexOf("EUR") == 0
            }
        } finally {
            event(downTime, MotionEvent.ACTION_UP, x, bottom)
        }
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun ordinarySwipesScrollWithoutReordering() {
        val initialOrder = order.get()
        val first = boundsFor(reorderLabel("EUR"))
        val x = first.centerX().toFloat()
        val fromY = first.centerY().toFloat()
        val toY = fromY - 180 * context.resources.displayMetrics.density
        val downTime = SystemClock.uptimeMillis()
        event(downTime, MotionEvent.ACTION_DOWN, x, fromY)
        try {
            move(downTime, x, fromY, toY)
        } finally {
            event(downTime, MotionEvent.ACTION_UP, x, toY)
        }
        waitUntil { boundsFor(reorderLabel("EUR")).centerY() < fromY - 100 }
        assertEquals(initialOrder, order.get())
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun pullingDownRefreshesOnceUntilTheCurrentRefreshFinishes() {
        val initialOrder = order.get()
        pullDown(220f)
        waitUntil { refreshCount.get() == 1 }
        assertEquals(initialOrder, order.get())

        pullDown(220f)
        SystemClock.sleep(400)
        assertEquals("An active refresh must not restart", 1, refreshCount.get())

        scenario.onActivity { refreshing = false }
        waitUntil { nodeWithDescription(context.getString(R.string.refresh_rates)) != null }
        SystemClock.sleep(400)
        pullDown(220f)
        waitUntil { refreshCount.get() == 2 }
        assertEquals(initialOrder, order.get())
    }

    @Test
    fun shortPullsDoNotRefreshOrReorder() {
        val initialOrder = order.get()
        pullDown(50f)
        SystemClock.sleep(400)
        assertEquals(0, refreshCount.get())
        assertEquals(initialOrder, order.get())
    }

    @Test
    fun pullingDownAwayFromTheTopScrollsWithoutRefreshing() {
        val initialOrder = order.get()
        val density = context.resources.displayMetrics.density
        val third = boundsFor(reorderLabel("CHF"))
        swipe(third.centerX().toFloat(), third.centerY().toFloat(), third.centerY() - 300 * density)

        val second = boundsFor(reorderLabel("GBP"))
        val fromY = second.centerY().toFloat()
        swipe(second.centerX().toFloat(), fromY, fromY + 220 * density)
        waitUntil { boundsFor(reorderLabel("GBP")).centerY() > fromY + 100 }
        SystemClock.sleep(400)
        assertEquals(0, refreshCount.get())
        assertEquals(initialOrder, order.get())
    }

    @Test
    fun cancellationSettlesTheCardAndAllowsAnotherDrag() {
        val first = boundsFor(reorderLabel("EUR"))
        val second = boundsFor(reorderLabel("GBP"))
        val x = first.centerX().toFloat()
        val endY = second.centerY() + 24f
        val downTime = beginDrag(x, first.centerY().toFloat())
        move(downTime, x, first.centerY().toFloat(), endY)
        event(downTime, MotionEvent.ACTION_CANCEL, x, endY)
        waitUntil { order.get().take(2) == listOf("GBP", "EUR") }
        SystemClock.sleep(400)
        val settled = boundsFor(reorderLabel("EUR"))
        assertEquals("The cancelled drag should settle", second.centerY().toFloat(), settled.centerY().toFloat(), 8f)
        val nextDown = beginDrag(x, settled.centerY().toFloat())
        try {
            move(nextDown, x, settled.centerY().toFloat(), first.centerY() - 24f)
            waitUntil { order.get().take(2) == listOf("EUR", "GBP") }
        } finally {
            event(nextDown, MotionEvent.ACTION_UP, x, first.centerY() - 24f)
        }
    }

    @Test
    fun accessibilityActionsOnlyOfferValidMoves() {
        val up = context.getString(R.string.move_currency_up, "EUR")
        val down = context.getString(R.string.move_currency_down, "EUR")
        assertFalse(hasAction(up))
        val node = waitForNode { it.actionList.any { action -> action.label?.toString() == down } }
        val action = node.actionList.first { it.label?.toString() == down }
        assertTrue(node.performAction(action.id))
        waitUntil { order.get().take(2) == listOf("GBP", "EUR") }
        waitUntil { hasAction(up) }
    }

    @Test
    fun currencyOrderIsRestoredByANewRepository() {
        val preferences = context.getSharedPreferences("fxcalc", Context.MODE_PRIVATE)
        val oldBase = preferences.getString("base_currency", null)
        val oldTargets = preferences.getString("target_currencies", null)
        try {
            RatesRepository(context).saveCurrencies("USD", listOf("JPY", "CHF", "EUR", "GBP"))
            val reopened = RatesRepository(context)
            assertEquals("USD", reopened.baseCurrency())
            assertEquals(listOf("JPY", "CHF", "EUR", "GBP"), reopened.targetCurrencies("USD"))
        } finally {
            preferences.edit()
                .putString("base_currency", oldBase)
                .putString("target_currencies", oldTargets)
                .commit()
        }
    }

    private fun reorderLabel(code: String) = context.getString(R.string.reorder_currency, code)

    private fun boundsFor(description: String): Rect = Rect().also { bounds ->
        waitForNode { it.contentDescription?.toString() == description }.getBoundsInScreen(bounds)
    }

    private fun nodeWithDescription(description: String): AccessibilityNodeInfo? =
        findNode(root()) {
            it.contentDescription?.toString() == description
        }

    private fun hasAction(label: String): Boolean =
        findNode(root()) { node ->
            node.actionList.any { it.label?.toString() == label }
        } != null

    private fun waitForNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        waitUntil {
            result = findNode(root(), predicate)
            result != null
        }
        return checkNotNull(result)
    }

    private fun root(): AccessibilityNodeInfo? {
        // Graphics-layer movement can leave cached accessibility bounds behind.
        instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }

    private fun findNode(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            findNode(root.getChild(index), predicate)?.let { return it }
        }
        return null
    }

    private fun beginDrag(x: Float, y: Float): Long {
        val downTime = SystemClock.uptimeMillis()
        event(downTime, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
        return downTime
    }

    private fun pullDown(distanceDp: Float) {
        val bounds = boundsFor(reorderLabel("EUR"))
        val fromY = bounds.centerY().toFloat()
        swipe(bounds.centerX().toFloat(), fromY, fromY + distanceDp * context.resources.displayMetrics.density)
    }

    private fun swipe(x: Float, fromY: Float, toY: Float) {
        val downTime = SystemClock.uptimeMillis()
        event(downTime, MotionEvent.ACTION_DOWN, x, fromY)
        try {
            move(downTime, x, fromY, toY)
            SystemClock.sleep(120)
            event(downTime, MotionEvent.ACTION_MOVE, x, toY)
        } finally {
            event(downTime, MotionEvent.ACTION_UP, x, toY)
        }
    }

    private fun move(downTime: Long, x: Float, fromY: Float, toY: Float) {
        for (step in 1..30) {
            event(downTime, MotionEvent.ACTION_MOVE, x, fromY + (toY - fromY) * step / 30)
            SystemClock.sleep(16)
        }
    }

    private fun event(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun waitUntil(message: () -> String = { "Timed out waiting for the UI" }, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message(), condition())
    }
}

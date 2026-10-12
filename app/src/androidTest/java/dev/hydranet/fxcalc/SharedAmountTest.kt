package dev.hydranet.fxcalc

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAmountTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun share(text: String) = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    @Test
    fun sharedAmountIsFilledAndSurvivesRecreation() {
        ActivityScenario.launch<MainActivity>(share(" 25,5 ")).use { scenario ->
            scenario.onActivity { assertEquals("25,5", it.amountText) }
            scenario.recreate()
            scenario.onActivity { assertEquals("25,5", it.amountText) }
        }
    }

    @Test
    fun newSharesReplaceAmountAndInvalidTextPreservesIt() {
        ActivityScenario.launch<MainActivity>(share("25")).use { scenario ->
            listOf("50.5" to "50.5", "invalid" to "50.5", "25" to "25").forEach { (text, expected) ->
                scenario.onActivity { it.startActivity(share(text)) }
                val deadline = SystemClock.uptimeMillis() + 5_000
                var received = false
                while (!received && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { received = it.intent.getStringExtra(Intent.EXTRA_TEXT) == text }
                    if (!received) SystemClock.sleep(50)
                }
                assertTrue("Share was not delivered: $text", received)
                scenario.onActivity { assertEquals(expected, it.amountText) }
            }
        }
    }
}

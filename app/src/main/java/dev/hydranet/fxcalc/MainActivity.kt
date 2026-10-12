package dev.hydranet.fxcalc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.hydranet.fxcalc.ui.theme.FXCalcTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repository: RatesRepository
    private var rates by mutableStateOf<ExchangeRates?>(null)
    private var refreshing by mutableStateOf(false)
    private var refreshFailed by mutableStateOf(false)
    private var rateSource by mutableStateOf(RateSource.default)
    private var baseCurrency by mutableStateOf("USD")
    private var targets by mutableStateOf<List<String>>(emptyList())
    private var quickConvertEnabled by mutableStateOf(false)
    internal var amountText by mutableStateOf("100")
        private set
    private var refreshJob: Job? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setQuickConvert(true)
            } else {
                Toast.makeText(this, R.string.quick_convert_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = RatesRepository(this)
        rateSource = repository.source()
        rates = repository.cached(rateSource)
        baseCurrency = repository.baseCurrency()
        targets = repository.targetCurrencies(baseCurrency)
        amountText = savedInstanceState?.getString("amount") ?: "100"
        if (savedInstanceState == null) handleSharedAmount(intent)

        setContent {
            FXCalcTheme {
                ConverterScreen(
                    rates = rates,
                    refreshing = refreshing,
                    refreshFailed = refreshFailed,
                    rateSource = rateSource,
                    baseCode = baseCurrency,
                    targetCodes = targets,
                    amountText = amountText,
                    onAmountChange = { amountText = it },
                    onRefresh = ::refreshRates,
                    onSourceSelected = ::selectSource,
                    onBaseSelected = ::selectBase,
                    onTargetAdded = { changeCurrencies(baseCurrency, targets + it) },
                    onTargetRemoved = { code -> changeCurrencies(baseCurrency, targets - code) },
                    onTargetMoved = { from, to ->
                        val fromIndex = targets.indexOf(from)
                        val toIndex = targets.indexOf(to)
                        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                            changeCurrencies(
                                baseCurrency,
                                targets.toMutableList().apply { add(toIndex, removeAt(fromIndex)) },
                            )
                        }
                    },
                    onSwap = ::swapWith,
                    quickConvertEnabled = quickConvertEnabled,
                    onQuickConvertChange = ::setQuickConvert,
                )
            }
        }
        refreshRates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedAmount(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("amount", amountText)
        super.onSaveInstanceState(outState)
    }

    private fun handleSharedAmount(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedAmount = parseSharedAmount(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        if (sharedAmount != null) {
            amountText = sharedAmount
        } else {
            Toast.makeText(this, R.string.shared_amount_invalid, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        quickConvertEnabled = repository.quickConvertEnabled() && hasNotificationPermission()
        // Brings the notification back if it was swiped away while the setting is on.
        QuickConvertNotification.update(this)
    }

    private fun hasNotificationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun setQuickConvert(enabled: Boolean) {
        if (!enabled) {
            QuickConvertNotification.disable(this)
        } else if (!hasNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        } else {
            QuickConvertNotification.enable(this)
        }
        quickConvertEnabled = enabled
    }

    private fun refreshRates() {
        val source = rateSource
        refreshJob?.cancel()
        refreshing = true
        refreshFailed = false
        refreshJob = lifecycleScope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { repository.fetch(source) }
                if (rateSource == source) rates = fetched
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (rateSource == source) refreshFailed = true
            } finally {
                if (refreshJob == coroutineContext[Job]) refreshing = false
            }
        }
    }

    private fun selectSource(source: RateSource) {
        if (source == rateSource) return
        rateSource = source
        repository.saveSource(source)
        rates = repository.cached(source)
        refreshFailed = false
        refreshRates()
    }

    private fun selectBase(code: String) {
        if (code == baseCurrency) return
        val oldBase = baseCurrency
        val updated = targets.filterNot { it == code }
        changeCurrencies(code, if (oldBase in updated) updated else listOf(oldBase) + updated)
    }

    private fun swapWith(code: String) {
        val oldBase = baseCurrency
        changeCurrencies(code, targets.map { if (it == code) oldBase else it })
    }

    private fun changeCurrencies(base: String, selected: List<String>) {
        baseCurrency = base
        targets = selected.distinct().filter { it != base }
        repository.saveCurrencies(baseCurrency, targets)
    }
}

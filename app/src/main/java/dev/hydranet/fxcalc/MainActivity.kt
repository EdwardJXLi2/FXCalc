package dev.hydranet.fxcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = RatesRepository(this)
        rateSource = repository.source()
        rates = repository.cached(rateSource)
        baseCurrency = repository.baseCurrency()
        targets = repository.targetCurrencies(baseCurrency)

        setContent {
            FXCalcTheme {
                ConverterScreen(
                    rates = rates,
                    refreshing = refreshing,
                    refreshFailed = refreshFailed,
                    rateSource = rateSource,
                    baseCode = baseCurrency,
                    targetCodes = targets,
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
                )
            }
        }
        refreshRates()
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

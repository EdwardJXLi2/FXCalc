package dev.hydranet.fxcalc

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private enum class PickerMode { BASE, TARGET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConverterScreen(
    rates: ExchangeRates?,
    refreshing: Boolean,
    refreshFailed: Boolean,
    rateSource: RateSource,
    baseCode: String,
    targetCodes: List<String>,
    amountText: String,
    onAmountChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSourceSelected: (RateSource) -> Unit,
    onBaseSelected: (String) -> Unit,
    onTargetAdded: (String) -> Unit,
    onTargetRemoved: (String) -> Unit,
    onTargetMoved: (String, String) -> Unit,
    onSwap: (String) -> Unit,
    quickConvertEnabled: Boolean,
    onQuickConvertChange: (Boolean) -> Unit,
) {
    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }
    var showRateSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dragState = rememberCurrencyDragState(listState, targetCodes, onTargetMoved)

    val amount = amountText.replace(',', '.').toBigDecimalOrNull()
    val supported = CurrencyCatalog.supported(rates)
    val baseInfo = CurrencyCatalog.find(baseCode) ?: CurrencyCatalog.find("USD")!!
    val addable = supported.filter { it.code != baseCode && it.code !in targetCodes }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconToggleButton(
                        checked = quickConvertEnabled,
                        onCheckedChange = onQuickConvertChange,
                    ) {
                        Icon(
                            if (quickConvertEnabled) {
                                Icons.Outlined.NotificationsActive
                            } else {
                                Icons.Outlined.NotificationsNone
                            },
                            contentDescription = stringResource(R.string.quick_convert_toggle),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh_rates),
                            )
                        }
                    }
                    IconButton(onClick = { showRateSettings = true }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.rate_source),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (addable.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { pickerMode = PickerMode.TARGET },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_currency)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderCurrencies(dragState),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    end = 20.dp,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "amount") {
                    AmountPanel(
                        amountText = amountText,
                        baseInfo = baseInfo,
                        onAmountChange = { if (isAmountInput(it)) onAmountChange(it) },
                        onBaseClick = { pickerMode = PickerMode.BASE },
                    )
                }

                if (rates == null || refreshing || refreshFailed) {
                    item(key = "status") {
                        RateStatusLine(
                            rates = rates,
                            refreshing = refreshing,
                            refreshFailed = refreshFailed,
                        )
                    }
                }

                item(key = "currencies_header") {
                    Text(
                        stringResource(R.string.currencies),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (targetCodes.isEmpty()) {
                    item(key = "empty") {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.empty_currencies),
                                modifier = Modifier.padding(20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(targetCodes, key = { _, code -> code }) { index, code ->
                        val info = CurrencyCatalog.find(code) ?: return@itemsIndexed
                        val moveUpLabel = stringResource(R.string.move_currency_up, code)
                        val moveDownLabel = stringResource(R.string.move_currency_down, code)
                        val isDragging = dragState.draggingCode == code
                        val isRaised = isDragging || dragState.settlingCode == code
                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.015f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "${code}DragScale",
                        )
                        ConversionCard(
                            info = info,
                            starred = quickConvertEnabled && index == 0,
                            baseCode = baseCode,
                            amount = amount,
                            rates = rates,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = if (isRaised) {
                                        null
                                    } else {
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        )
                                    },
                                    fadeOutSpec = null,
                                )
                                .zIndex(if (isRaised) 1f else 0f)
                                .graphicsLayer {
                                    translationY = dragState.offset(code)
                                    scaleX = dragScale
                                    scaleY = dragScale
                                }
                                .semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(CustomAccessibilityAction(moveUpLabel) {
                                                onTargetMoved(code, targetCodes[index - 1])
                                                true
                                            })
                                        }
                                        if (index < targetCodes.lastIndex) {
                                            add(CustomAccessibilityAction(moveDownLabel) {
                                                onTargetMoved(code, targetCodes[index + 1])
                                                true
                                            })
                                        }
                                    }
                                },
                            onSwap = { onSwap(code) },
                            onRemove = { onTargetRemoved(code) },
                        )
                    }
                }

                item(key = "source") {
                    val date = rates?.date?.format(
                        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()),
                    )
                    Text(
                        if (date == null) rateSource.title else "${rateSource.title} · $date",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pickerMode?.let { mode ->
        CurrencyPicker(
            title = stringResource(
                if (mode == PickerMode.BASE) R.string.from_currency else R.string.add_currency,
            ),
            options = if (mode == PickerMode.BASE) supported else addable,
            onDismiss = { pickerMode = null },
            onSelect = { code ->
                if (mode == PickerMode.BASE) onBaseSelected(code) else onTargetAdded(code)
                pickerMode = null
            },
        )
    }

    if (showRateSettings) {
        RateSourceDialog(
            selected = rateSource,
            rates = rates,
            refreshing = refreshing,
            refreshFailed = refreshFailed,
            onChoose = onSourceSelected,
            onDismiss = { showRateSettings = false },
        )
    }
}

@Composable
private fun AmountPanel(
    amountText: String,
    baseInfo: CurrencyInfo,
    onAmountChange: (String) -> Unit,
    onBaseClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = contentColor,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            TextButton(
                onClick = onBaseClick,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            ) {
                Text(
                    stringResource(R.string.from_currency),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(baseInfo.code, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor,
                    cursorColor = contentColor,
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.7f),
                    focusedLabelColor = contentColor,
                    unfocusedLabelColor = contentColor,
                ),
            )
        }
    }
}

@Composable
private fun RateStatusLine(
    rates: ExchangeRates?,
    refreshing: Boolean,
    refreshFailed: Boolean,
) {
    val date = rates?.date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    val message = when {
        rates == null && refreshing -> stringResource(R.string.loading_rates)
        rates == null && refreshFailed -> stringResource(R.string.load_failed)
        rates == null -> stringResource(R.string.rates_unavailable)
        refreshFailed -> stringResource(R.string.refresh_failed, date.orEmpty())
        refreshing -> stringResource(R.string.refreshing_rates)
        else -> null
    }
    if (message != null) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (refreshFailed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateInformation(source: RateSource, rates: ExchangeRates?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rates != null) {
                RateDetail(
                    label = stringResource(R.string.rate_date),
                    value = rates.date.format(
                        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()),
                    ),
                )
                rates.retrievedAt?.let { retrievedAt ->
                    RateDetail(
                        label = stringResource(R.string.rates_downloaded),
                        value = retrievedAt.atZone(ZoneId.systemDefault()).format(
                            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()),
                        ),
                    )
                }
            }
            Text(
                source.attribution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.reference_rates_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateDetail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RateSourceDialog(
    selected: RateSource,
    rates: ExchangeRates?,
    refreshing: Boolean,
    refreshFailed: Boolean,
    onChoose: (RateSource) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_source)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.selectableGroup()) {
                    RateSource.entries.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .selectable(
                                    selected = source == selected,
                                    role = Role.RadioButton,
                                    onClick = { onChoose(source) },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = source == selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    source.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(source.summaryRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RateStatusLine(
                        rates = rates,
                        refreshing = refreshing,
                        refreshFailed = refreshFailed,
                    )
                    RateInformation(source = selected, rates = rates)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun ConversionCard(
    info: CurrencyInfo,
    starred: Boolean,
    baseCode: String,
    amount: BigDecimal?,
    rates: ExchangeRates?,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val converted = amount?.let { rates?.convert(it, baseCode, info.code) }
    val unitRate = rates?.convert(BigDecimal.ONE, baseCode, info.code)
    val secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info.code,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (starred) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.quick_convert_starred),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        info.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        contentDescription = stringResource(R.string.reorder_currency, info.code),
                        modifier = Modifier.size(18.dp),
                        tint = secondaryContentColor,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                converted?.let { formatAmount(it, info.code) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    unitRate?.let { "1 $baseCode = ${formatRate(it)} ${info.code}" }
                        ?: stringResource(
                            if (rates == null) R.string.loading_rates else R.string.rates_unavailable,
                        ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onSwap) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = stringResource(R.string.use_as_base, info.code),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.remove_currency, info.code),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyPicker(
    title: String,
    options: List<CurrencyInfo>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(options, search) {
        val query = search.trim()
        options.filter {
            it.code.contains(query, ignoreCase = true) ||
                it.country.contains(query, ignoreCase = true) ||
                it.name.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search)) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            IconButton(onClick = { search = "" }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.currency_count, filtered.size, filtered.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.no_currencies), modifier = Modifier.padding(12.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(filtered, key = { it.code }) { info ->
                            ListItem(
                                headlineContent = { Text("${info.country} · ${info.code}") },
                                supportingContent = { Text(info.name) },
                                modifier = Modifier.clickable { onSelect(info.code) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

internal fun formatAmount(value: BigDecimal, code: String): String {
    val digits = Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}

internal fun formatRate(value: BigDecimal): String =
    value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

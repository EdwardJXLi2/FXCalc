package dev.hydranet.fxcalc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun rememberCurrencyDragState(
    listState: LazyListState,
    codes: List<String>,
    onMove: (String, String) -> Unit,
): CurrencyDragState {
    val currentCodes by rememberUpdatedState(codes)
    val currentOnMove by rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edgeSize = with(LocalDensity.current) { 72.dp.toPx() }
    val maxScrollSpeed = with(LocalDensity.current) { 600.dp.toPx() }
    return remember(listState, scope, haptics, edgeSize, maxScrollSpeed) {
        CurrencyDragState(
            listState = listState,
            scope = scope,
            codes = { currentCodes },
            edgeSize = edgeSize,
            maxScrollSpeed = maxScrollSpeed,
            onMove = { from, to -> currentOnMove(from, to) },
            onStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
}

internal class CurrencyDragState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val codes: () -> List<String>,
    private val edgeSize: Float,
    private val maxScrollSpeed: Float,
    private val onMove: (String, String) -> Unit,
    private val onStart: () -> Unit,
) {
    var draggingCode by mutableStateOf<String?>(null)
        private set
    var settlingCode by mutableStateOf<String?>(null)
        private set
    private var draggedTop by mutableFloatStateOf(0f)
    private var pointerY = 0f
    private var session: CurrencyReorderSession? = null
    private var scrollJob: Job? = null
    private var settleJob: Job? = null
    private val settleOffset = Animatable(0f)

    fun offset(code: String): Float = when (code) {
        draggingCode -> {
            val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == code }
            row?.let { draggedTop - it.offset } ?: 0f
        }
        settlingCode -> settleOffset.value
        else -> 0f
    }

    fun canStartAt(y: Float): Boolean = rowAt(y) != null

    fun start(y: Float) {
        val row = rowAt(y) ?: return
        settleJob?.cancel()
        settlingCode = null
        session = CurrencyReorderSession(row)
        draggingCode = row.code
        draggedTop = row.top.toFloat()
        pointerY = y
        onStart()
        scrollJob = scope.launch {
            var previousFrame = withFrameNanos { it }
            while (isActive) {
                val frame = withFrameNanos { it }
                val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                previousFrame = frame
                val layout = listState.layoutInfo
                val topEdge = layout.viewportStartOffset + layout.beforeContentPadding
                val bottomEdge = layout.viewportEndOffset + layout.beforeContentPadding - edgeSize
                val speed = when {
                    pointerY < topEdge + edgeSize ->
                        -((topEdge + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
                    pointerY > bottomEdge ->
                        ((pointerY - bottomEdge) / edgeSize).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (speed != 0f) listState.scrollBy(speed * maxScrollSpeed * seconds)
                moveIfNeeded()
            }
        }
    }

    fun drag(distance: Float) {
        val current = session ?: return
        current.dragBy(distance)
        draggedTop = current.top
        pointerY += distance
        moveIfNeeded()
    }

    fun finish() {
        if (session == null) return
        scrollJob?.cancel()
        session = null
        settleJob = scope.launch {
            // Let an outstanding reorder reach layout before settling into the new slot.
            withFrameNanos { }
            val code = draggingCode ?: return@launch
            settleOffset.snapTo(offset(code))
            settlingCode = code
            draggingCode = null
            try {
                settleOffset.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            } finally {
                settlingCode = null
            }
        }
    }

    private fun moveIfNeeded() {
        val current = session ?: return
        val target = current.target(positions()) ?: return
        // Keep the viewport steady when the first visible row changes position.
        listState.requestScrollToItem(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
        )
        onMove(current.code, target.code)
    }

    private fun positions(): List<CurrencyRowPosition> {
        val selected = codes()
        return listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            val code = item.key as? String ?: return@mapNotNull null
            if (code !in selected) return@mapNotNull null
            CurrencyRowPosition(code, item.index, item.offset, item.size)
        }
    }

    private fun rowAt(y: Float): CurrencyRowPosition? {
        val contentY = y - listState.layoutInfo.beforeContentPadding
        return positions().firstOrNull { contentY >= it.top && contentY < it.top + it.height }
    }
}

internal fun Modifier.reorderCurrencies(state: CurrencyDragState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!state.canStartAt(down.position.y)) return@awaitEachGesture
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            state.start(press.position.y)
            try {
                while (state.draggingCode != null) {
                    // Claim movement before the list's scroll handler can take over.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == press.id } ?: break
                    val distance = change.positionChange().y
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) {
                        break
                    }
                    state.drag(distance)
                }
            } finally {
                state.finish()
            }
        }
    }

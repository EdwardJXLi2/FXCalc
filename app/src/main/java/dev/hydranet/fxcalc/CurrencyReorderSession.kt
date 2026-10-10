package dev.hydranet.fxcalc

internal data class CurrencyRowPosition(
    val code: String,
    val index: Int,
    val top: Int,
    val height: Int,
) {
    val center: Float get() = top + height / 2f
}

internal class CurrencyReorderSession(start: CurrencyRowPosition) {
    val code = start.code
    val height = start.height
    var top = start.top.toFloat()
        private set
    private var pendingTarget: String? = null
    private var movingDown = false

    fun dragBy(distance: Float) {
        top += distance
    }

    fun target(rows: List<CurrencyRowPosition>): CurrencyRowPosition? {
        val current = rows.firstOrNull { it.code == code } ?: return null
        // Pointer events can arrive before the previous move has been laid out.
        val pending = rows.firstOrNull { it.code == pendingTarget }
        if (pending != null) {
            val applied = if (movingDown) current.index > pending.index else current.index < pending.index
            if (!applied) return null
        }
        pendingTarget = null

        val center = top + height / 2f
        val target = if (center > current.center) {
            rows.lastOrNull { it.index > current.index && center >= it.center }
        } else {
            rows.firstOrNull { it.index < current.index && center <= it.center }
        }
        pendingTarget = target?.code
        movingDown = target != null && target.index > current.index
        return target
    }
}

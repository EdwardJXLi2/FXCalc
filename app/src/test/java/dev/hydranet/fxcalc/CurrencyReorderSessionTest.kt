package dev.hydranet.fxcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyReorderSessionTest {
    private val rows = listOf(
        CurrencyRowPosition("EUR", 3, 200, 100),
        CurrencyRowPosition("GBP", 4, 312, 100),
        CurrencyRowPosition("CHF", 5, 424, 100),
        CurrencyRowPosition("JPY", 6, 536, 100),
    )

    @Test
    fun waitsUntilTheDraggedCenterCrossesAnotherCenter() {
        val session = CurrencyReorderSession(rows[0])
        session.dragBy(111f)
        assertNull(session.target(rows))
        session.dragBy(1f)
        assertEquals("GBP", session.target(rows)?.code)
    }

    @Test
    fun ignoresStaleLayoutUntilTheMoveIsApplied() {
        val session = CurrencyReorderSession(rows[0])
        session.dragBy(120f)
        assertEquals("GBP", session.target(rows)?.code)
        repeat(10) {
            session.dragBy(1f)
            assertNull(session.target(rows))
        }

        val moved = listOf(rows[1].copy(index = 3, top = 200), rows[0].copy(index = 4, top = 312)) + rows.drop(2)
        assertNull(session.target(moved))
        assertEquals(330f, session.top, 0f)
    }

    @Test
    fun canReverseDirectionWithoutSwappingBackWhileStationary() {
        val session = CurrencyReorderSession(rows[0])
        session.dragBy(130f)
        assertEquals("GBP", session.target(rows)?.code)
        val moved = listOf(rows[1].copy(index = 3, top = 200), rows[0].copy(index = 4, top = 312)) + rows.drop(2)
        repeat(10) { assertNull(session.target(moved)) }
        session.dragBy(-131f)
        assertEquals("GBP", session.target(moved)?.code)
    }

    @Test
    fun handlesFastDragsAcrossMultipleRows() {
        val session = CurrencyReorderSession(rows[0])
        session.dragBy(350f)
        assertEquals("JPY", session.target(rows)?.code)
    }

    @Test
    fun usesEachRowsActualHeight() {
        val unequal = listOf(rows[0], rows[1].copy(height = 200))
        val session = CurrencyReorderSession(unequal[0])
        session.dragBy(161f)
        assertNull(session.target(unequal))
        session.dragBy(1f)
        assertEquals("GBP", session.target(unequal)?.code)
    }

    @Test
    fun headerChangesDoNotLeaveAMovePending() {
        val session = CurrencyReorderSession(rows[0])
        session.dragBy(130f)
        assertEquals("GBP", session.target(rows)?.code)
        val moved = listOf(
            rows[1].copy(index = 2, top = 180),
            rows[0].copy(index = 3, top = 292),
            rows[2].copy(index = 4, top = 404),
        )
        session.dragBy(125f)
        assertEquals("CHF", session.target(moved)?.code)
    }

    @Test
    fun doesNotTargetHeadersOrMovePastTheLastCurrency() {
        val session = CurrencyReorderSession(rows.last())
        session.dragBy(1000f)
        assertNull(session.target(rows))
    }
}

package app.nemla.core.domain

import app.nemla.core.domain.model.DeferredDebit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DeferredDebitTest {
    private val bnp = DeferredDebit(enabled = true, cutoffDay = 31, debitDay = 5, since = null)
    private val creditAgricole = DeferredDebit(enabled = true, cutoffDay = 25, debitDay = 1, since = null)

    @Test
    fun immediateDebitKeepsThePurchaseDate() {
        assertEquals(LocalDate(2026, 1, 10), DeferredDebit.Immediate.debitDateOf(LocalDate(2026, 1, 10)))
    }

    @Test
    fun purchasesBeforeTheCutoffAreDebitedTheFollowingMonth() {
        assertEquals(LocalDate(2026, 2, 5), bnp.debitDateOf(LocalDate(2026, 1, 10)))
        assertEquals(LocalDate(2026, 2, 5), bnp.debitDateOf(LocalDate(2026, 1, 31)))
    }

    @Test
    fun purchasesAfterTheCutoffMoveOneMonthFurther() {
        assertEquals(LocalDate(2026, 3, 1), creditAgricole.debitDateOf(LocalDate(2026, 1, 26)))
        assertEquals(LocalDate(2026, 2, 1), creditAgricole.debitDateOf(LocalDate(2026, 1, 25)))
    }

    @Test
    fun cutoffIsBoundedByTheLengthOfTheMonth() {
        val rule = bnp.copy(cutoffDay = 30)
        assertEquals(LocalDate(2026, 3, 5), rule.debitDateOf(LocalDate(2026, 2, 28)))
        assertEquals(LocalDate(2026, 3, 5), rule.debitDateOf(LocalDate(2026, 2, 27)))
    }

    @Test
    fun debitDayIsBoundedByTheTargetMonth() {
        val rule = bnp.copy(debitDay = 31)
        assertEquals(LocalDate(2026, 2, 28), rule.debitDateOf(LocalDate(2026, 1, 15)))
        assertEquals(LocalDate(2026, 1, 1), bnp.copy(debitDay = 0).debitDateOf(LocalDate(2025, 12, 3)))
    }

    @Test
    fun yearRollsOverInDecember() {
        assertEquals(LocalDate(2027, 1, 5), bnp.debitDateOf(LocalDate(2026, 12, 24)))
        assertEquals(LocalDate(2027, 2, 1), creditAgricole.debitDateOf(LocalDate(2026, 12, 28)))
    }

    @Test
    fun purchasesBeforeTheStartDateAreNotDeferred() {
        val rule = bnp.copy(since = LocalDate(2026, 6, 1))
        assertEquals(LocalDate(2026, 5, 31), rule.debitDateOf(LocalDate(2026, 5, 31)))
        assertEquals(LocalDate(2026, 7, 5), rule.debitDateOf(LocalDate(2026, 6, 1)))
    }
}

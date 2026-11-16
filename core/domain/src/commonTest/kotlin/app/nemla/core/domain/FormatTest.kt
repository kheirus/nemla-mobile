package app.nemla.core.domain

import app.nemla.core.domain.format.DateLabels
import app.nemla.core.domain.format.MoneyFormat
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun euroUsesFrenchGroupingAndAlwaysTwoDecimals() {
        assertEquals("1 234,50 €", MoneyFormat.format(123_450, Currency.EUR))
        assertEquals("12,00 €", MoneyFormat.format(1_200, Currency.EUR))
        assertEquals("0,05 €", MoneyFormat.format(5, Currency.EUR))
        assertEquals("-1 000 000,00 €", MoneyFormat.format(-100_000_000, Currency.EUR))
    }

    @Test
    fun englishCurrenciesPutTheSymbolFirst() {
        assertEquals("£1,234.50", MoneyFormat.format(123_450, Currency.GBP))
        assertEquals("-$12.30", MoneyFormat.format(-1_230, Currency.USD))
    }

    @Test
    fun otherCurrenciesFollowTheirLocale() {
        assertEquals("1 234,50 $", MoneyFormat.format(123_450, Currency.CAD))
        assertEquals("1.234,50 MAD", MoneyFormat.format(123_450, Currency.MAD))
        assertEquals("1 234,5 F CFA", MoneyFormat.format(123_450, Currency.XOF))
        assertEquals("1 234 F CFA", MoneyFormat.format(123_400, Currency.XOF))
        assertEquals("10,00 CHF", MoneyFormat.format(1_000, Currency.CHF))
    }

    @Test
    fun compactAmountsFollowTheAxisRules() {
        assertEquals("0 €", MoneyFormat.compact(0, Currency.EUR))
        assertEquals("800 €", MoneyFormat.compact(80_000, Currency.EUR))
        assertEquals("13 €", MoneyFormat.compact(1_250, Currency.EUR))
        assertEquals("999 €", MoneyFormat.compact(99_940, Currency.EUR))
        assertEquals("1,2 k€", MoneyFormat.compact(123_456, Currency.EUR))
        assertEquals("1 k€", MoneyFormat.compact(100_000, Currency.EUR))
        assertEquals("10 k€", MoneyFormat.compact(999_960, Currency.EUR))
        assertEquals("12 k€", MoneyFormat.compact(1_234_500, Currency.EUR))
        assertEquals("13 k€", MoneyFormat.compact(1_250_000, Currency.EUR))
        assertEquals("1,5 k$ CA", MoneyFormat.compact(150_000, Currency.CAD))
        assertEquals("-12 €", MoneyFormat.compact(-1_250, Currency.EUR))
    }

    @Test
    fun shortDatesUseTwoDigitDaysAndShortMonths() {
        assertEquals("05 août", DateLabels.short(LocalDate(2026, 8, 5), AppLanguage.FR))
        assertEquals("05 Aug", DateLabels.short(LocalDate(2026, 8, 5), AppLanguage.EN))
        assertEquals("16 nov.", DateLabels.short(LocalDate(2026, 11, 16), AppLanguage.FR))
    }

    @Test
    fun longDatesAndMonthLabels() {
        assertEquals("5 octobre 2026", DateLabels.long(LocalDate(2026, 10, 5), AppLanguage.FR))
        assertEquals("5 October 2026", DateLabels.long(LocalDate(2026, 10, 5), AppLanguage.EN))
        assertEquals("août 2026", DateLabels.monthYear(YearMonth(2026, 8), AppLanguage.FR))
        assertEquals("Août 2026", DateLabels.capitalizeFirst(DateLabels.monthYear(YearMonth(2026, 8), AppLanguage.FR)))
        assertEquals("August 2026", DateLabels.monthYear(YearMonth(2026, 8), AppLanguage.EN))
    }

    @Test
    fun monthInitialsForTheChart() {
        assertEquals("JFMAMJJASOND", (1..12).joinToString("") { DateLabels.monthInitial(it, AppLanguage.FR) })
        assertEquals("JFMAMJJASOND", (1..12).joinToString("") { DateLabels.monthInitial(it, AppLanguage.EN) })
        assertEquals("", DateLabels.capitalizeFirst("   "))
    }
}

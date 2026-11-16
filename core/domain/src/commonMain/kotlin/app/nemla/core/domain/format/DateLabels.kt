package app.nemla.core.domain.format

import app.nemla.core.domain.model.AppLanguage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

object DateLabels {
    private val frenchLong =
        listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private val frenchShort = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val englishLong =
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val englishShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")

    fun short(
        date: LocalDate,
        language: AppLanguage,
    ): String = date.day.toString().padStart(2, '0') + " " + monthShort(date.month.number, language)

    fun long(
        date: LocalDate,
        language: AppLanguage,
    ): String = "${date.day} ${monthLong(date.month.number, language)} ${date.year}"

    fun monthYear(
        month: YearMonth,
        language: AppLanguage,
    ): String = "${monthLong(month.month.number, language)} ${month.year}"

    fun monthShort(
        monthNumber: Int,
        language: AppLanguage,
    ): String = (if (language == AppLanguage.EN) englishShort else frenchShort)[monthNumber - 1]

    fun monthLong(
        monthNumber: Int,
        language: AppLanguage,
    ): String = (if (language == AppLanguage.EN) englishLong else frenchLong)[monthNumber - 1]

    fun monthInitial(
        monthNumber: Int,
        language: AppLanguage,
    ): String = monthShort(monthNumber, language).take(1).uppercase()

    fun capitalizeFirst(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) trimmed else trimmed.substring(0, 1).uppercase() + trimmed.substring(1)
    }
}

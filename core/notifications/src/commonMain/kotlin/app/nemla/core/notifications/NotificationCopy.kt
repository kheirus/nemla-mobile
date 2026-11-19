package app.nemla.core.notifications

import app.nemla.core.domain.format.DateLabels
import app.nemla.core.domain.format.MoneyFormat
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.ReviewPeriod

internal class NoticeText(
    val title: String,
    val body: String,
) {
    override fun toString(): String = "NoticeText(***)"
}

internal enum class NoticeChannel(
    val id: String,
) {
    REMINDERS("reminders"),
    REPORTS("reports"),
    BUDGETS("budgets"),
    MESSAGES("messages"),
    ;

    companion object {
        fun of(notice: LocalNotice): NoticeChannel =
            when (notice) {
                LocalNotice.DailyReminder -> REMINDERS
                is LocalNotice.ReportArrived -> REPORTS
                is LocalNotice.BudgetReached -> BUDGETS
                is LocalNotice.ServerMessage -> MESSAGES
            }

        fun fromId(id: String?): NoticeChannel = entries.firstOrNull { it.id == id } ?: MESSAGES
    }
}

internal object NotificationCopy {
    const val BRAND: String = "nemla"

    fun text(
        notice: LocalNotice,
        language: AppLanguage,
    ): NoticeText {
        val english = language == AppLanguage.EN
        return when (notice) {
            LocalNotice.DailyReminder -> {
                NoticeText(
                    BRAND,
                    if (english) {
                        "Daily check-in: dictate today's expenses in 30 seconds."
                    } else {
                        "Bilan de la journée : dictez vos dépenses du jour en 30 secondes."
                    },
                )
            }

            is LocalNotice.ReportArrived -> {
                val period = periodLabel(notice.period, language)
                NoticeText(BRAND, if (english) "Your report for $period is ready" else "Votre bilan de $period est arrivé")
            }

            is LocalNotice.BudgetReached -> {
                val used = MoneyFormat.format(notice.usedCents, notice.currency)
                val allocated = MoneyFormat.format(notice.allocatedCents, notice.currency)
                if (english) {
                    NoticeText("Budget reached", "${notice.categoryName}: $used spent of $allocated planned this month.")
                } else {
                    NoticeText("Budget atteint", "${notice.categoryName} : $used dépensés sur $allocated prévus ce mois-ci.")
                }
            }

            is LocalNotice.ServerMessage -> {
                NoticeText(notice.title.ifBlank { BRAND }, notice.body)
            }
        }
    }

    fun channelName(
        channel: NoticeChannel,
        language: AppLanguage,
    ): String {
        val english = language == AppLanguage.EN
        return when (channel) {
            NoticeChannel.REMINDERS -> if (english) "Daily reminder" else "Rappel quotidien"
            NoticeChannel.REPORTS -> if (english) "Reports" else "Bilans"
            NoticeChannel.BUDGETS -> if (english) "Budget alerts" else "Alertes de budget"
            NoticeChannel.MESSAGES -> if (english) "nemla messages" else "Messages nemla"
        }
    }

    fun isPrivate(notice: LocalNotice): Boolean = notice is LocalNotice.BudgetReached || notice is LocalNotice.ServerMessage

    fun periodLabel(
        period: ReviewPeriod,
        language: AppLanguage,
    ): String =
        when (period) {
            is ReviewPeriod.Month -> DateLabels.capitalizeFirst(DateLabels.monthYear(period.month, language))
            is ReviewPeriod.Year -> if (language == AppLanguage.EN) "the year ${period.year}" else "l'année ${period.year}"
        }
}

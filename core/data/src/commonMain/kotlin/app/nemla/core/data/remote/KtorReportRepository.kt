package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.map
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.port.ReportRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.yearMonth
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal class ReportViewDto(
    val mois: String? = null,
)

@Serializable
internal class ExpenseDateDto(
    val date: String? = null,
)

internal class KtorReportRepository(
    private val postgrest: Postgrest,
) : ReportRepository {
    private val client get() = postgrest.client

    override suspend fun viewedPeriods(): AppResult<Set<ReviewPeriod>> =
        postgrest.withUser { userId ->
            safeRequest<List<ReportViewDto>> {
                client.get(postgrest.table(REPORT_VIEWS)) {
                    parameter("select", "mois")
                    parameter("user_id", eq(userId))
                }
            }.map { rows -> rows.mapNotNull { row -> row.mois?.let(ReportCalendar::parseKey) }.toSet() }
        }

    override suspend fun markViewed(period: ReviewPeriod): AppResult<Unit> =
        postgrest.withUser { userId ->
            val result =
                safeRequest<Unit> {
                    client.post(postgrest.table(REPORT_VIEWS)) {
                        parameter("on_conflict", "user_id,mois")
                        header(PREFER_HEADER, "resolution=ignore-duplicates,return=minimal")
                        jsonBody(
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("user_id", userId)
                                        put("mois", ReportCalendar.key(period))
                                    },
                                ),
                            ),
                        )
                    }
                }
            if (result is AppResult.Failure && result.error == AppError.Validation(ValidationCode.ALREADY_EXISTS)) {
                AppResult.Success(Unit)
            } else {
                result
            }
        }

    override suspend fun monthsWithConfirmedExpenses(span: DateSpan): AppResult<Set<YearMonth>> =
        postgrest.withUser { userId ->
            val months = mutableSetOf<YearMonth>()
            var upTo = span.to
            var round = 0
            while (round < MAX_ROUNDS && upTo >= span.from) {
                val bound = upTo
                val result =
                    safeRequest<List<ExpenseDateDto>> {
                        client.get(postgrest.table("expenses")) {
                            parameter("select", "date")
                            parameter("user_id", eq(userId))
                            parameter("confirmed", "is.true")
                            parameter("date", "gte.${span.from}")
                            parameter("date", "lte.$bound")
                            parameter("order", "date.desc")
                            parameter("limit", EXPENSE_PAGE_SIZE)
                        }
                    }
                val dates =
                    when (result) {
                        is AppResult.Failure -> return@withUser result
                        is AppResult.Success -> result.value.mapNotNull { row -> row.date?.let(LocalDate::parseOrNullSafe) }
                    }
                dates.filter { it in span }.forEach { months += it.yearMonth }
                val oldest = dates.minOrNull() ?: break
                if (result.value.size < EXPENSE_PAGE_SIZE) break
                upTo = oldest.yearMonth.firstDay.minus(1, DateTimeUnit.DAY)
                round++
            }
            AppResult.Success(months)
        }

    private companion object {
        const val REPORT_VIEWS = "report_views"
        const val MAX_ROUNDS = 60
    }
}

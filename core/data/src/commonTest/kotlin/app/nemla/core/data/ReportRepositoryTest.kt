package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.ReviewPeriod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportRepositoryTest {
    private val window = DateSpan(LocalDate(2023, 1, 1), LocalDate(2026, 10, 31))

    @Test
    fun viewedPeriodsAreParsedAndMalformedKeysIgnored() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""[{"mois":"2026-10"},{"mois":"2025"},{"mois":"oct"},{}]""") }
            val viewed = harness.reports.viewedPeriods()
            assertEquals(AppResult.Success(setOf(ReviewPeriod.Month(YearMonth(2026, 10)), ReviewPeriod.Year(2025))), viewed)
            val request = harness.requests.single()
            assertEquals("/rest/v1/report_views", request.path)
            assertEquals("mois", request.query("select"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
        }

    @Test
    fun markingAViewInsertsThePeriodKeyAndIgnoresDuplicates() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            assertEquals(AppResult.Success(Unit), harness.reports.markViewed(ReviewPeriod.Year(2026)))
            val request = harness.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/rest/v1/report_views", request.path)
            assertEquals("user_id,mois", request.query("on_conflict"))
            assertTrue(
                request.data.headers["Prefer"]
                    .orEmpty()
                    .contains("resolution=ignore-duplicates"),
            )
            assertEquals("""[{"user_id":"$USER_ID","mois":"2026"}]""", request.body)

            val duplicate = DataHarness(store = signedInStore()) { json("""{"code":"23505","message":"dup"}""", HttpStatusCode.Conflict) }
            assertEquals(AppResult.Success(Unit), duplicate.reports.markViewed(ReviewPeriod.Month(YearMonth(2026, 8))))
            assertEquals("""[{"user_id":"$USER_ID","mois":"2026-08"}]""", duplicate.requests.single().body)
        }

    @Test
    fun signedOutUserSendsNothing() =
        runTest {
            val harness = DataHarness { json("[]") }
            assertEquals(AppError.Unauthorized, harness.reports.viewedPeriods().failure)
            assertEquals(AppError.Unauthorized, harness.reports.markViewed(ReviewPeriod.Year(2026)).failure)
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun confirmedMonthsAreReadWithinTheWindow() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json("""[{"date":"2026-10-30"},{"date":"2026-10-02"},{"date":"2025-01-15"},{"date":"bad"},{"date":"2022-12-31"}]""")
                }
            val months = harness.reports.monthsWithConfirmedExpenses(window)
            assertEquals(AppResult.Success(setOf(YearMonth(2026, 10), YearMonth(2025, 1))), months)
            val request = harness.requests.single()
            assertEquals("/rest/v1/expenses", request.path)
            assertEquals("date", request.query("select"))
            assertEquals("is.true", request.query("confirmed"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals(
                listOf("gte.2023-01-01", "lte.2026-10-31"),
                request.data.url.parameters
                    .getAll("date"),
            )
        }

    @Test
    fun fullPagesSkipToThePreviousMonth() =
        runTest {
            val fullOctober =
                (1..1_000).joinToString(
                    ",",
                    "[",
                    "]",
                ) { """{"date":"2026-10-${(it % 28 + 1).toString().padStart(2, '0')}"}""" }
            var call = 0
            val harness =
                DataHarness(store = signedInStore()) {
                    call++
                    if (call == 1) json(fullOctober) else json("""[{"date":"2024-03-04"}]""")
                }
            val months = harness.reports.monthsWithConfirmedExpenses(window)
            assertEquals(AppResult.Success(setOf(YearMonth(2026, 10), YearMonth(2024, 3))), months)
            assertEquals(2, harness.requests.size)
            assertEquals(
                listOf("gte.2023-01-01", "lte.2026-09-30"),
                harness.requests[1]
                    .data.url.parameters
                    .getAll("date"),
            )
        }

    @Test
    fun readFailureIsReturned() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }
            assertTrue(harness.reports.monthsWithConfirmedExpenses(window) is AppResult.Failure)
        }
}

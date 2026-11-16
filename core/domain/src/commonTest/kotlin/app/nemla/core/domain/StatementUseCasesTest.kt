package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.model.StatementFormat
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.model.StatementText
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.StatementImporter
import app.nemla.core.domain.usecase.ImportStatement
import app.nemla.core.domain.usecase.ImportedReviews
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementUseCasesTest {
    private val statement = StatementText("Date Libellé Débit Crédit\n02/03 CB LIDL 12,30", StatementFormat.CSV)
    private val draft = ExpenseDraft(ExpenseId("e-1"), 1_230, "Lidl", LocalDate(2026, 3, 2), null, null)

    private class RecordingImporter(
        private val result: AppResult<StatementImport>,
    ) : StatementImporter {
        var calls = 0

        override suspend fun import(
            statement: StatementText,
            language: AppLanguage,
            onAnonymized: (redactions: Int) -> Unit,
        ): AppResult<StatementImport> {
            calls++
            onAnonymized(3)
            return result
        }
    }

    private class FixedQuotas(
        private val result: AppResult<MonthlyQuota>,
    ) : QuotaRepository {
        override suspend fun currentMonth(): AppResult<MonthlyQuota> = result
    }

    private fun quota(importUsed: Int): AppResult<MonthlyQuota> = AppResult.Success(MonthlyQuota(YearMonth(2026, 11), 0, 30, importUsed, 1))

    @Test
    fun exhaustedImportQuotaStopsBeforeReadingTheStatement() =
        runTest {
            val importer = RecordingImporter(AppResult.Success(StatementImport(listOf(draft), 0)))
            val result = ImportStatement(importer, FixedQuotas(quota(importUsed = 1)))(statement, AppLanguage.FR)
            assertEquals(AppError.QuotaExceeded, (result as AppResult.Failure).error)
            assertEquals(0, importer.calls)
        }

    @Test
    fun remainingQuotaOrUnreadableQuotaLetsTheImporterRun() =
        runTest {
            val success = AppResult.Success(StatementImport(listOf(draft), 3))
            val counts = mutableListOf<Int>()
            val importer = RecordingImporter(success)
            assertEquals(success, ImportStatement(importer, FixedQuotas(quota(importUsed = 0)))(statement, AppLanguage.FR) { counts += it })
            val offline = RecordingImporter(success)
            assertEquals(
                success,
                ImportStatement(offline, FixedQuotas(AppResult.Failure(AppError.Network.Offline)))(statement, AppLanguage.EN),
            )
            assertEquals(listOf(3), counts)
            assertEquals(1, offline.calls)
        }

    @Test
    fun importedReviewsAreAnnouncedAndSkipDraftsWithoutId() {
        val review = ImportedReviews.of(listOf(draft, ExpenseDraft(null, 100, "x", LocalDate(2026, 3, 2), null, null)))
        assertEquals(ReviewOrigin.IMPORT, review.origin)
        assertEquals(listOf(draft), review.drafts)
        assertTrue(review.announce)
        assertEquals(null, review.voiceEntryId)
    }

    @Test
    fun statementTextHidesItsContent() {
        assertFalse(statement.toString().contains("LIDL"))
        assertTrue(statement.toString().contains("CSV"))
    }
}

package app.nemla.feature.statementimport

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.documents.DocumentKind
import app.nemla.core.documents.DocumentReadResult
import app.nemla.core.documents.DocumentText
import app.nemla.core.documents.DocumentTextReader
import app.nemla.core.documents.PickedDocument
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.model.StatementText
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.StatementImporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }
}

internal const val SYNTHETIC_STATEMENT = "Date;Libellé;Débit\n02/03/2026;CB BOULANGERIE TEST;3,80\n05/03/2026;PRLV OPERATEUR;19,99"

internal class FakeDocument(
    override val displayName: String = "releve-test.csv",
) : PickedDocument

internal class FakeReader(
    var result: DocumentReadResult = DocumentReadResult.Success(DocumentText(SYNTHETIC_STATEMENT, DocumentKind.CSV)),
) : DocumentTextReader {
    val reads = mutableListOf<PickedDocument>()

    override suspend fun read(document: PickedDocument): DocumentReadResult {
        reads += document
        return result
    }
}

internal fun draft(id: String?): ExpenseDraft =
    ExpenseDraft(id?.let(::ExpenseId), 380, "Boulangerie test", LocalDate(2026, 3, 2), null, null)

internal class FakeImporter : StatementImporter {
    val results = ArrayDeque<AppResult<StatementImport>>()
    val calls = mutableListOf<Pair<StatementText, AppLanguage>>()
    var redactions = 5
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun import(
        statement: StatementText,
        language: AppLanguage,
        onAnonymized: (redactions: Int) -> Unit,
    ): AppResult<StatementImport> {
        calls += statement to language
        onAnonymized(redactions)
        gate?.await()
        return results.removeFirstOrNull() ?: AppResult.Success(StatementImport(listOf(draft("e-1"), draft("e-2")), redactions))
    }
}

internal class FakeQuotas(
    var importUsed: Int = 0,
    var failing: Boolean = false,
) : QuotaRepository {
    var calls = 0

    override suspend fun currentMonth(): AppResult<MonthlyQuota> {
        calls++
        if (failing) return AppResult.Failure(AppError.Network.Offline)
        return AppResult.Success(MonthlyQuota(YearMonth(2026, 11), 0, 30, importUsed, 1))
    }
}

internal class FakeConsents(
    granted: Set<AiConsentLevel> = emptySet(),
) : AiConsentRepository {
    val state = MutableStateFlow(AiConsents(granted.associateWith { Instant.fromEpochSeconds(0) }))
    var grantResult: AppResult<Unit> = AppResult.Success(Unit)
    val grants = mutableListOf<AiConsentLevel>()

    override fun observe(): Flow<AiConsents> = state

    override suspend fun current(): AppResult<AiConsents> = AppResult.Success(state.value)

    override suspend fun grant(level: AiConsentLevel): AppResult<Unit> {
        grants += level
        if (grantResult is AppResult.Success) state.value = AiConsents(state.value.grants + (level to Instant.fromEpochSeconds(1)))
        return grantResult
    }

    override suspend fun revoke(level: AiConsentLevel): AppResult<Unit> {
        state.value = AiConsents(state.value.grants - level)
        return AppResult.Success(Unit)
    }
}

internal class FakePreferences(
    language: AppLanguage = AppLanguage.EN,
) : PreferencesRepository {
    private val state = MutableStateFlow<AppPreferences?>(AppPreferences(appLanguage = language))
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

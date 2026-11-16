package app.nemla.feature.voice

import app.nemla.core.audio.MicrophoneAccess
import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.RecorderState
import app.nemla.core.audio.VoiceRecorder
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.SpeechTranscriber
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.core.domain.usecase.ObserveAuthState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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

internal val testToday = LocalDate(2026, 11, 16)
internal val groceries = category("c-1", "Courses")
internal val leisure = category("c-2", "Loisirs")

internal fun category(
    id: String,
    name: String,
) = Category(CategoryId(id), name, "#E4572E", CategoryIcon.SHOPPING, CategoryType.VARIABLE, true, 1, true, CategoryBudget.None)

internal fun draft(
    id: String,
    label: String,
    cents: Long = 1_200,
    categoryId: CategoryId? = groceries.id,
    date: LocalDate = testToday,
) = ExpenseDraft(ExpenseId(id), cents, label, date, categoryId, null)

internal fun sampleCapture(vararg drafts: ExpenseDraft): VoiceCapture =
    VoiceCapture(VoiceEntryId("v-1"), "marché douze euros", drafts.toList().ifEmpty { listOf(draft("e-1", "Marché")) })

internal fun quota(
    used: Int,
    limit: Int = 30,
) = MonthlyQuota(YearMonth(2026, 11), used, limit, 0, 1)

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

internal class FakeVoiceRecorder : VoiceRecorder {
    val mutableState = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = mutableState
    var starts = 0
    var stops = 0
    var cancels = 0

    override fun start() {
        starts++
        mutableState.value = RecorderState.Recording(0)
    }

    override fun stop() {
        stops++
    }

    override fun cancel() {
        cancels++
        mutableState.value = RecorderState.Idle
    }

    fun finish(bytes: ByteArray = ByteArray(8_000) { 1 }) {
        mutableState.value = RecorderState.Finished(bytes, 16_000, 2_000)
    }
}

internal class FakeRecorderFactory(
    val recorder: FakeVoiceRecorder = FakeVoiceRecorder(),
) : VoiceRecorderFactory {
    override fun create(scope: CoroutineScope): VoiceRecorder = recorder
}

internal class FakeMicrophone(
    var access: MicrophoneAccess = MicrophoneAccess.GRANTED,
) : MicrophonePermission {
    override fun status(): MicrophoneAccess = access
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

internal class FakeCapturer : VoiceExpenseCapturer {
    val results = ArrayDeque<AppResult<VoiceCapture>>()
    val calls = mutableListOf<Pair<VoiceRecording, AppLanguage>>()
    val received = mutableListOf<ByteArray>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun capture(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<VoiceCapture> {
        calls += recording to language
        received += recording.wavBytes.copyOf()
        gate?.await()
        return results.removeFirstOrNull() ?: AppResult.Success(sampleCapture())
    }
}

internal class FakeQuotas(
    var result: AppResult<MonthlyQuota> = AppResult.Success(quota(used = 5)),
) : QuotaRepository {
    var calls = 0

    override suspend fun currentMonth(): AppResult<MonthlyQuota> {
        calls++
        return result
    }
}

internal class FakeCategories(
    var result: AppResult<List<Category>> = AppResult.Success(listOf(groceries, leisure)),
) : CategoryRepository {
    override suspend fun list(): AppResult<List<Category>> = result

    override suspend fun create(category: NewCategory): AppResult<Category> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: CategoryId): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeReviews : ExpenseReviewRepository {
    var learned: Set<String> = emptySet()
    var confirmResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    val confirmed = mutableListOf<ReviewedExpense>()
    val deleted = mutableListOf<ExpenseId>()
    val marked = mutableListOf<VoiceEntryId>()
    val outcomes = mutableListOf<CategoryOutcome>()

    override suspend fun learnedMerchantKeys(keys: Set<String>): AppResult<Set<String>> = AppResult.Success(keys intersect learned)

    override suspend fun confirm(expense: ReviewedExpense): AppResult<Unit> {
        confirmed += expense
        return confirmResult
    }

    override suspend fun delete(id: ExpenseId): AppResult<Unit> {
        deleted += id
        return deleteResult
    }

    override suspend fun markVoiceEntryConfirmed(id: VoiceEntryId): AppResult<Unit> {
        marked += id
        return AppResult.Success(Unit)
    }

    override suspend fun recordCategoryOutcome(outcome: CategoryOutcome): AppResult<Unit> {
        outcomes += outcome
        return AppResult.Success(Unit)
    }

    override suspend fun logCorrection(correction: ClassificationCorrection): AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)
}

internal val testSession = Session(UserId("user-1"), email = null, expiresAt = null)

internal class FakeAuth(
    initial: AuthState = AuthState.SignedIn(testSession),
) : AuthRepository {
    val state = MutableStateFlow(initial)

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(testSession)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(testSession)
}

internal fun testStore(
    auth: FakeAuth = FakeAuth(),
    scope: CoroutineScope,
): VoiceReviewStore = VoiceReviewStore(ObserveAuthState(auth), scope)

internal class FakeTranscriber : SpeechTranscriber {
    val results = ArrayDeque<AppResult<String>>()
    val languages = mutableListOf<AppLanguage>()
    val received = mutableListOf<ByteArray>()

    override suspend fun transcribe(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<String> {
        languages += language
        received += recording.wavBytes
        return results.removeFirstOrNull() ?: AppResult.Success("loyer 750 euros")
    }
}

internal class FakePreferences : PreferencesRepository {
    private val state = MutableStateFlow<AppPreferences?>(AppPreferences.Default)
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

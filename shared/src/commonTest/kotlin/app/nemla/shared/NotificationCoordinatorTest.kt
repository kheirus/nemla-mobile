package app.nemla.shared

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ScheduledNotice
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.LocalNotifier
import app.nemla.core.domain.port.NotificationMemory
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.usecase.ClearNotifications
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeliverPushInbox
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.RefreshScheduledNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCoordinatorTest {
    private val auth = CoordinatorAuth()
    private val preferences = CoordinatorPreferences()
    private val notifier = CountingNotifier()
    private val memory = CountingMemory()
    private val inbox = PushInbox()
    private val languages = CurrentLanguages(preferences, DeviceLanguage { AppLanguage.FR })

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun coordinator() =
        NotificationCoordinator(
            ObserveAuthState(auth),
            ObservePreferences(preferences),
            languages,
            RefreshScheduledNotifications(notifier = notifier, profiles = NoProfile, expenses = NoExpense, languages = languages),
            DeliverPushInbox(inbox, notifier, languages),
            ClearNotifications(notifier, memory),
        )

    private fun session(expiresAt: Long? = null) =
        AuthState.SignedIn(Session(UserId("u"), email = null, expiresAt = expiresAt?.let(Instant::fromEpochSeconds)))

    @Test
    fun nothingHappensBeforeSignIn() {
        val coordinator = coordinator()
        coordinator.onForeground()
        auth.state.value = AuthState.SignedOut
        assertTrue(notifier.scheduled.isEmpty())
        assertEquals(0, notifier.cancelled)
        assertEquals(0, memory.cleared)
    }

    @Test
    fun signingInSchedulesAndDeliversPushItemsOnce() {
        val coordinator = coordinator()
        auth.state.value = session()
        assertEquals(listOf(NoticeGroup.REPORT_ARRIVAL), notifier.scheduled)
        assertEquals(listOf("p-1"), notifier.shownTags)
        auth.state.value = session(expiresAt = 10)
        assertEquals(1, notifier.scheduled.size)
        coordinator.onForeground()
        assertEquals(2, notifier.scheduled.size)
        assertEquals(listOf(InboxItemId("p-1")), inbox.delivered)
    }

    @Test
    fun languageChangesRescheduleInTheNewLanguage() {
        coordinator()
        preferences.state.value = AppPreferences(appLanguage = AppLanguage.FR)
        auth.state.value = session()
        assertEquals(1, notifier.scheduled.size)
        preferences.state.value = AppPreferences(appLanguage = AppLanguage.FR)
        assertEquals(1, notifier.scheduled.size)
        preferences.state.value = AppPreferences(appLanguage = AppLanguage.EN)
        assertEquals(2, notifier.scheduled.size)
        assertEquals(AppLanguage.EN, notifier.languages.last())
    }

    @Test
    fun signingOutClearsEverythingOnce() {
        coordinator()
        auth.state.value = session()
        auth.state.value = AuthState.SignedOut
        assertEquals(1, notifier.cancelled)
        assertEquals(1, memory.cleared)
        auth.state.value = session()
        assertEquals(2, notifier.scheduled.size)
    }
}

private class CoordinatorAuth : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.Unknown)
    private val session = Session(UserId("u"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}

private class CoordinatorPreferences : PreferencesRepository {
    val state = MutableStateFlow<AppPreferences?>(null)
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

private class CountingNotifier : LocalNotifier {
    val scheduled = mutableListOf<NoticeGroup>()
    val languages = mutableListOf<AppLanguage>()
    val shownTags = mutableListOf<String>()
    var cancelled = 0

    override suspend fun access(): NotificationAccess = NotificationAccess.GRANTED

    override suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit> {
        scheduled += group
        languages += language
        return AppResult.Success(Unit)
    }

    override suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit> {
        shownTags += tag
        return AppResult.Success(Unit)
    }

    override suspend fun cancelAll(): AppResult<Unit> {
        cancelled++
        return AppResult.Success(Unit)
    }
}

private class CountingMemory : NotificationMemory {
    var cleared = 0

    override suspend fun wasAlerted(key: String): AppResult<Boolean> = AppResult.Success(false)

    override suspend fun remember(key: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun clear(): AppResult<Unit> {
        cleared++
        return AppResult.Success(Unit)
    }
}

private class PushInbox : InboxRepository {
    val delivered = mutableListOf<InboxItemId>()

    override suspend fun unseen(): AppResult<List<InboxItem>> =
        AppResult.Success(
            listOf(InboxItem(InboxItemId("p-1"), InboxChannel.PUSH, InboxKind.DAILY_REMINDER, "t", "b", null, null, false))
                .filterNot { it.id in delivered },
        )

    override suspend fun markSeen(id: InboxItemId): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun markDelivered(id: InboxItemId): AppResult<Unit> {
        delivered += id
        return AppResult.Success(Unit)
    }
}

private object NoProfile : ProfileRepository {
    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun getProfile(): AppResult<UserProfile> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> = AppResult.Failure(AppError.Unknown)
}

private object NoExpense : ExpenseRepository {
    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> = AppResult.Success(emptyList())

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Success(emptyList())

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: ExpenseId): AppResult<Unit> = AppResult.Success(Unit)
}

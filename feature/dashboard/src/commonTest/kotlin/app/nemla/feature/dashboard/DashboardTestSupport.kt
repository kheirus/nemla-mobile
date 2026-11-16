package app.nemla.feature.dashboard

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.usecase.BootstrapAndLoadProfile
import app.nemla.core.domain.usecase.DeleteExpense
import app.nemla.core.domain.usecase.GetDeferredDebit
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListExpenses
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.MaterializeRecurringCharges
import app.nemla.core.domain.usecase.SearchExpensesByDate
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.UpdateExpense
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Instant

internal val testToday = LocalDate(2026, 11, 16)

internal fun category(
    id: String,
    name: String,
    budget: CategoryBudget = CategoryBudget.None,
    visible: Boolean = true,
    color: String = "#E4572E",
) = Category(CategoryId(id), name, color, CategoryIcon.SHOPPING, CategoryType.VARIABLE, false, 1, visible, budget)

internal val groceries = category("c-1", "Courses")
internal val leisure = category("c-2", "Loisirs")
internal val hidden = category("c-3", "Cachée", visible = false)

private var sequence = 0L

internal fun expense(
    id: String,
    cents: Long,
    date: LocalDate = testToday,
    categoryId: CategoryId? = groceries.id,
    confirmed: Boolean = true,
    voice: String? = null,
    source: ExpenseSource = ExpenseSource.VOICE,
    method: PaymentMethod = PaymentMethod.CARD,
) = Expense(
    id = ExpenseId(id),
    categoryId = categoryId,
    voiceEntryId = voice?.let(::VoiceEntryId),
    recurringChargeId = null,
    amountCents = cents,
    date = date,
    debitDate = date,
    source = source,
    paymentMethod = method,
    confirmed = confirmed,
    createdAt = Instant.fromEpochSeconds(1_000 + sequence++),
)

internal fun charge(
    id: String,
    cents: Long,
    frequency: ChargeFrequency = ChargeFrequency.MONTHLY,
) = RecurringCharge(RecurringChargeId(id), "Charge $id", cents, frequency, 5, null)

internal fun profile(
    monthlyCents: Long = 0,
    annualCents: Long? = null,
    pseudo: String? = "Léa",
    currency: Currency = Currency.EUR,
) = UserProfile(
    userId = UserId("user-1"),
    pseudo = pseudo,
    age = null,
    gender = null,
    household = null,
    currency = currency,
    budget = GlobalBudget(monthlyCents, annualCents, BudgetPeriod.MONTH),
    onboardingDone = true,
    dailyReminder = true,
)

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

internal class FakeProfiles(
    var result: AppResult<UserProfile> = AppResult.Success(profile()),
) : ProfileRepository {
    var bootstraps = 0

    override suspend fun bootstrap(): AppResult<Unit> {
        bootstraps++
        return AppResult.Success(Unit)
    }

    override suspend fun getProfile(): AppResult<UserProfile> = result

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(available = true, suggestions = emptyList()))

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeCategories(
    var result: AppResult<List<Category>> = AppResult.Success(listOf(groceries, leisure, hidden)),
) : CategoryRepository {
    override suspend fun list(): AppResult<List<Category>> = result

    override suspend fun create(category: NewCategory): AppResult<Category> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: CategoryId): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeExpenses : ExpenseRepository {
    val years = mutableMapOf<Int, List<Expense>>()
    var month: List<Expense> = emptyList()
    var pending: List<Expense> = emptyList()
    var search: List<Expense> = emptyList()
    var failure: AppError? = null
    var yearGate: CompletableDeferred<Unit>? = null
    var updateResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    val queries = mutableListOf<ExpenseQuery>()
    val updates = mutableListOf<Pair<ExpenseId, ExpenseEdit>>()
    val deletes = mutableListOf<ExpenseId>()

    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> {
        queries += query
        failure?.let { return AppResult.Failure(it) }
        return AppResult.Success(
            when {
                query.confirmed == false -> {
                    pending
                }

                query.dateEquals != null -> {
                    search
                }

                isSingleMonth(query) -> {
                    month
                }

                else -> {
                    yearGate?.await()
                    years[query.dateFrom?.year].orEmpty()
                }
            },
        )
    }

    private fun isSingleMonth(query: ExpenseQuery): Boolean {
        val from = query.dateFrom ?: return false
        val to = query.dateTo ?: return false
        return from.month == to.month && from.year == to.year
    }

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> {
        updates += id to edit
        return updateResult
    }

    override suspend fun delete(id: ExpenseId): AppResult<Unit> {
        deletes += id
        return deleteResult
    }
}

internal class FakeRecurring(
    var result: AppResult<List<RecurringCharge>> = AppResult.Success(emptyList()),
) : RecurringChargeRepository {
    override suspend fun list(): AppResult<List<RecurringCharge>> = result

    override suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: RecurringChargeId): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeMaterializer : RecurringChargeMaterializer {
    val years = mutableListOf<Int>()

    override suspend fun materialize(year: Int): AppResult<Int> {
        years += year
        return AppResult.Failure(AppError.NotSupportedYet)
    }
}

internal class FakeDebits(
    var result: AppResult<DeferredDebit> = AppResult.Success(DeferredDebit.Immediate),
) : DeferredDebitRepository {
    override suspend fun current(): AppResult<DeferredDebit> = result

    override suspend fun settings(): AppResult<DebitSettings> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> = AppResult.Failure(AppError.NotSupportedYet)
}

internal class FakeAuth : AuthRepository {
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)
    var signOuts = 0
    private val session = Session(UserId("user-1"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedIn(session))

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> {
        signOuts++
        return signOutResult
    }

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}

internal class DashboardHarness {
    val profiles = FakeProfiles()
    val categories = FakeCategories()
    val expenses = FakeExpenses()
    val recurring = FakeRecurring()
    val materializer = FakeMaterializer()
    val debits = FakeDebits()
    val auth = FakeAuth()
    var today: LocalDate = testToday
    var language: AppLanguage = AppLanguage.FR

    fun viewModel(): DashboardViewModel =
        DashboardViewModel(
            loadProfile = BootstrapAndLoadProfile(profiles),
            listCategories = ListCategories(categories),
            listExpenses = ListExpenses(expenses),
            searchByDate = SearchExpensesByDate(expenses),
            listRecurring = ListRecurringCharges(recurring),
            materialize = MaterializeRecurringCharges(materializer),
            getDeferredDebit = GetDeferredDebit(debits),
            updateExpense = UpdateExpense(expenses),
            deleteExpense = DeleteExpense(expenses),
            signOut = SignOut(auth),
            clock = { today },
            language = { language },
        )
}

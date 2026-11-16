package app.nemla.feature.settings

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AccountExport
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryEstimate
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.EstimateBasis
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.MonthSpending
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.Password
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
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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

internal val testToday = LocalDate(2026, 11, 16)

internal fun category(
    id: String,
    name: String,
    isDefault: Boolean = true,
    type: CategoryType = CategoryType.VARIABLE,
    budget: CategoryBudget = CategoryBudget.None,
    color: String = "#E4572E",
) = Category(CategoryId(id), name, color, CategoryIcon.TAG, type, isDefault, 1, true, budget)

internal fun profile(
    pseudo: String? = "Léa",
    budget: GlobalBudget = GlobalBudget(150_000, 1_800_000, BudgetPeriod.MONTH),
    currency: Currency = Currency.EUR,
) = UserProfile(UserId("u"), pseudo, 34, Gender.FEMALE, Household.FAMILY, currency, budget, true, true)

internal fun estimate(
    monthly: Long = 123_000,
    months: Int = 3,
    perCategory: List<CategoryEstimate> = listOf(CategoryEstimate(CategoryId("c-food"), 30_000, 3)),
) = BudgetEstimate(
    monthlyCents = monthly,
    annualCents = monthly * 12,
    fixedMonthlyCents = 0,
    months = List(months) { MonthSpending(YearMonth(2026, 8 + it), 100_000) },
    perCategory = perCategory,
    basis = EstimateBasis.Average(months),
)

internal class FakeProfiles : ProfileRepository {
    var profile: AppResult<UserProfile> = AppResult.Success(profile())
    var pseudo = PseudoAvailability(true, emptyList())
    var currencyResult: AppResult<Unit> = AppResult.Success(Unit)
    val budgets = mutableListOf<GlobalBudget>()
    val identities = mutableListOf<ProfileIdentity>()
    val currencies = mutableListOf<Currency>()

    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getProfile(): AppResult<UserProfile> = profile

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> {
        identities += identity
        return AppResult.Success(Unit)
    }

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> {
        budgets += budget
        return AppResult.Success(Unit)
    }

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> = AppResult.Success(this.pseudo)

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> {
        currencies += currency
        return currencyResult
    }

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeCategories : CategoryRepository {
    var categories =
        mutableListOf(
            category("c-food", "Courses"),
            category("c-rent", "Logement", type = CategoryType.FIXED),
            category("c-trip", "Vacances", isDefault = false, budget = CategoryBudget.Annual(120_000)),
        )
    val created = mutableListOf<NewCategory>()
    val updates = mutableListOf<Pair<CategoryId, CategoryUpdate>>()
    val deleted = mutableListOf<CategoryId>()
    var updateResult: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun list(): AppResult<List<Category>> = AppResult.Success(categories.toList())

    override suspend fun create(category: NewCategory): AppResult<Category> {
        created += category
        val made = category(category.name.lowercase(), category.name, isDefault = false, color = category.colorHex)
        categories += made
        return AppResult.Success(made)
    }

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> {
        updates += id to update
        return updateResult
    }

    override suspend fun delete(id: CategoryId): AppResult<Unit> {
        deleted += id
        categories.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }
}

internal class FakeCharges : RecurringChargeRepository {
    val charges = mutableListOf<RecurringCharge>()
    val added = mutableListOf<RecurringChargeDraft>()
    val deleted = mutableListOf<RecurringChargeId>()

    override suspend fun list(): AppResult<List<RecurringCharge>> = AppResult.Success(charges.toList())

    override suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit> {
        added += drafts
        drafts.forEach {
            charges +=
                RecurringCharge(RecurringChargeId("r-${charges.size}"), it.name, it.amountCents, it.frequency, it.dayOfMonth, it.categoryId)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: RecurringChargeId): AppResult<Unit> {
        deleted += id
        charges.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }
}

internal class FakeParser : RecurringChargeParser {
    var result: AppResult<List<RecurringChargeDraft>> =
        AppResult.Success(listOf(RecurringChargeDraft("Netflix", 1_549, ChargeFrequency.MONTHLY, 5, CategoryId("c-food"))))
    val calls = mutableListOf<Pair<String, AppLanguage>>()

    override suspend fun parse(
        text: String,
        language: AppLanguage,
    ): AppResult<List<RecurringChargeDraft>> {
        calls += text to language
        return result
    }
}

internal class FakeAdvisor : BudgetAdvisor {
    var result: AppResult<BudgetEstimate> = AppResult.Success(estimate())

    override suspend fun suggest(): AppResult<BudgetEstimate> = result
}

internal class FakeDebits : DeferredDebitRepository {
    var settings = DebitSettings(null, DeferredDebit.Immediate, choiceMade = false)
    var moved = 3
    val saved = mutableListOf<Pair<String?, DeferredDebit>>()

    override suspend fun current(): AppResult<DeferredDebit> = AppResult.Success(settings.rule)

    override suspend fun settings(): AppResult<DebitSettings> = AppResult.Success(settings)

    override suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> {
        saved += bankId to rule
        return AppResult.Success(moved)
    }
}

internal class FakeQuotas : QuotaRepository {
    var result: AppResult<MonthlyQuota> = AppResult.Success(MonthlyQuota(YearMonth(2026, 11), 20, 30, 1, 1))

    override suspend fun currentMonth(): AppResult<MonthlyQuota> = result
}

internal class FakeAccounts : AccountRepository {
    var deleteResult: AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun deleteAccount(): AppResult<Unit> = deleteResult

    override suspend fun exportData(): AppResult<AccountExport> = AppResult.Failure(AppError.NotSupportedYet)
}

internal class FakePreferences : PreferencesRepository {
    val state = MutableStateFlow<AppPreferences?>(AppPreferences.Default)
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

internal class FakeConsents(
    granted: Boolean = true,
) : AiConsentRepository {
    val state =
        MutableStateFlow(if (granted) AiConsents(mapOf(AiConsentLevel.PERSONAL to Instant.fromEpochSeconds(0))) else AiConsents.None)

    override fun observe(): Flow<AiConsents> = state

    override suspend fun current(): AppResult<AiConsents> = AppResult.Success(state.value)

    override suspend fun grant(level: AiConsentLevel): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun revoke(level: AiConsentLevel): AppResult<Unit> {
        state.value = AiConsents(state.value.grants - level)
        return AppResult.Success(Unit)
    }
}

internal class FakeAuth : AuthRepository {
    var signOuts = 0
    private val session = Session(UserId("u"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedIn(session))

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> {
        signOuts++
        return AppResult.Success(Unit)
    }

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}

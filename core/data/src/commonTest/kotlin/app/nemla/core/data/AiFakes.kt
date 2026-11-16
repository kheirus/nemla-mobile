package app.nemla.core.data

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiContextSource
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AiRouter
import app.nemla.ai.api.AiRoutingContext
import app.nemla.ai.api.AiTraceListener
import app.nemla.ai.api.DataSensitivity
import app.nemla.ai.api.EngineDescriptor
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.LocalFirstPolicy
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import kotlinx.datetime.YearMonth
import app.nemla.core.domain.model.GlobalBudget as Budget

internal val fullConsent: Set<DataSensitivity> = setOf(DataSensitivity.ANONYMIZED, DataSensitivity.PERSONAL)

internal class FixedContextSource(
    var context: AiRoutingContext = AiRoutingContext(online = true, remainingCredits = 10, remoteConsent = fullConsent),
) : AiContextSource {
    override suspend fun current(): AiRoutingContext = context
}

internal class FakeEngine<I : Any, O : Any>(
    override val capability: AiCapability<I, O>,
    id: String,
    location: ExecutionLocation = ExecutionLocation.REMOTE,
    cost: Int = 0,
    private val behaviour: suspend (I) -> O,
) : AiEngine<I, O> {
    override val descriptor: EngineDescriptor = EngineDescriptor(id, "fake", location, cost)
    val inputs = mutableListOf<I>()

    override suspend fun execute(input: I): O {
        inputs += input
        return behaviour(input)
    }
}

internal fun router(
    vararg engines: AiEngine<*, *>,
    context: FixedContextSource = FixedContextSource(),
    listener: AiTraceListener? = null,
): AiRouter = AiRouter(engines.toList(), context, LocalFirstPolicy, listener)

internal val sampleCategory =
    Category(CategoryId("cat-1"), "Courses", "#E4572E", CategoryIcon.SHOPPING, CategoryType.VARIABLE, true, 1, true, CategoryBudget.None)

internal class FakeCategoryRepository(
    var categories: List<Category> = listOf(sampleCategory),
) : CategoryRepository {
    var listCalls = 0

    override suspend fun list(): AppResult<List<Category>> {
        listCalls++
        return AppResult.Success(categories)
    }

    override suspend fun create(category: NewCategory): AppResult<Category> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun delete(id: CategoryId): AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)
}

internal class FakeProfileRepository(
    private val pseudo: String? = "camille",
) : ProfileRepository {
    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getProfile(): AppResult<UserProfile> =
        AppResult.Success(
            UserProfile(
                UserId(USER_ID),
                pseudo,
                null,
                null,
                null,
                Currency.EUR,
                GlobalBudget(0, null, BudgetPeriod.MONTH),
                onboardingDone = true,
                dailyReminder = true,
            ),
        )

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: Budget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(true, emptyList()))
}

internal class FakeQuotaRepository(
    var result: AppResult<MonthlyQuota> = AppResult.Success(MonthlyQuota(YearMonth(2026, 11), 28, 30, 0, 1)),
) : QuotaRepository {
    var calls = 0

    override suspend fun currentMonth(): AppResult<MonthlyQuota> {
        calls++
        return result
    }
}

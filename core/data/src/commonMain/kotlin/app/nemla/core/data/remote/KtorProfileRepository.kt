package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.data.session.GoTrueApi
import app.nemla.core.data.session.genderFromBackend
import app.nemla.core.data.session.householdFromBackend
import app.nemla.core.data.session.toBackend
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
internal class ProfileDto(
    @SerialName("monthly_budget") val monthlyBudget: JsonElement? = null,
    @SerialName("annual_budget") val annualBudget: JsonElement? = null,
    @SerialName("budget_periode") val budgetPeriod: String? = null,
    val pseudo: String? = null,
    val sexe: String? = null,
    val age: Int? = null,
    @SerialName("situation_familiale") val household: String? = null,
    @SerialName("daily_reminder") val dailyReminder: Boolean = true,
    val devise: String? = null,
    @SerialName("onboarding_done") val onboardingDone: Boolean = false,
) {
    override fun toString(): String = "ProfileDto(***)"

    fun toDomain(userId: String): UserProfile =
        UserProfile(
            userId = UserId(userId),
            pseudo = pseudo,
            age = age,
            gender = genderFromBackend(sexe),
            household = householdFromBackend(household),
            currency = Currency.fromCode(devise),
            budget =
                GlobalBudget(
                    monthlyCents = monthlyBudget.toCents() ?: 0,
                    annualCents = annualBudget.toCents(),
                    period = if (budgetPeriod == PERIOD_YEAR) BudgetPeriod.YEAR else BudgetPeriod.MONTH,
                ),
            onboardingDone = onboardingDone,
            dailyReminder = dailyReminder,
        )
}

@Serializable
internal class PseudoSuggestionDto(
    val available: Boolean = false,
    val suggestions: List<String> = emptyList(),
)

@Serializable
internal class PseudoQueryDto(
    @SerialName("p_pseudo") val pseudo: String,
)

internal const val PERIOD_MONTH = "mois"
internal const val PERIOD_YEAR = "annee"

private const val PROFILE_COLUMNS =
    "monthly_budget,annual_budget,budget_periode,pseudo,sexe,age,situation_familiale,daily_reminder,devise,onboarding_done"
private const val IDENTITY_COLUMNS = "pseudo,age,sexe,situation_familiale"
private val signupMetadataKeys = listOf("pseudo", "age", "sexe", "situation_familiale")

internal class KtorProfileRepository(
    private val postgrest: Postgrest,
    private val goTrue: GoTrueApi,
    private val logger: AppLogger,
) : ProfileRepository {
    private val client get() = postgrest.client

    override suspend fun bootstrap(): AppResult<Unit> {
        val bootstrapped =
            safeRequest<Unit> {
                client.post(postgrest.rpc("bootstrap_user")) { jsonBody(JsonObject(emptyMap())) }
            }
        if (bootstrapped is AppResult.Success) {
            val synced = syncSignupMetadata()
            if (synced is AppResult.Failure) logger.warn(DataLog.PROFILE_SYNC_FAILED)
        }
        return bootstrapped
    }

    override suspend fun getProfile(): AppResult<UserProfile> =
        postgrest.withUser { userId ->
            safeRequest<List<ProfileDto>> {
                client.get(postgrest.table("profiles")) {
                    parameter("select", PROFILE_COLUMNS)
                    parameter("id", eq(userId))
                }
            }.flatMap { rows ->
                rows.firstOrNull()?.let { AppResult.Success(it.toDomain(userId)) } ?: AppResult.Failure(AppError.NotFound)
            }
        }

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> =
        patchProfile(
            buildJsonObject {
                put("pseudo", identity.pseudo?.trim()?.takeIf { it.isNotEmpty() })
                put("sexe", identity.gender?.toBackend())
                put("age", identity.age)
                put("situation_familiale", identity.household?.toBackend())
                put("devise", identity.currency.code)
            },
        )

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> =
        patchProfile(
            buildJsonObject {
                put("monthly_budget", centsToJson(budget.monthlyCents))
                put("annual_budget", centsToJson(budget.annualCents))
                put("budget_periode", if (budget.period == BudgetPeriod.YEAR) PERIOD_YEAR else PERIOD_MONTH)
            },
        )

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> =
        patchProfile(buildJsonObject { put("daily_reminder", enabled) })

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> =
        patchProfile(buildJsonObject { put("devise", currency.code) })

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.post(postgrest.table("profiles")) {
                    parameter("on_conflict", "id")
                    header(PREFER_HEADER, "resolution=merge-duplicates,return=minimal")
                    jsonBody(
                        buildJsonObject {
                            put("id", userId)
                            put("monthly_budget", centsToJson(monthlyBudgetCents))
                            put("onboarding_done", true)
                        },
                    )
                }
            }
        }

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> {
        val trimmed = pseudo.trim()
        if (trimmed.isEmpty()) return AppResult.Success(PseudoAvailability(available = true, suggestions = emptyList()))
        val result =
            safeRequest<PseudoSuggestionDto> {
                client.post(postgrest.rpc("suggest_pseudo")) { jsonBody(PseudoQueryDto(trimmed)) }
            }
        return when (result) {
            is AppResult.Success -> AppResult.Success(PseudoAvailability(result.value.available, result.value.suggestions))
            is AppResult.Failure -> AppResult.Success(PseudoAvailability(available = true, suggestions = emptyList()))
        }
    }

    private suspend fun patchProfile(body: JsonObject): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.patch(postgrest.table("profiles")) {
                    parameter("id", eq(userId))
                    jsonBody(body)
                }
            }
        }

    private suspend fun syncSignupMetadata(): AppResult<Unit> =
        goTrue.currentUser().flatMap { user ->
            val metadata = user.userMetadata ?: return@flatMap AppResult.Success(Unit)
            safeRequest<List<JsonObject>> {
                client.get(postgrest.table("profiles")) {
                    parameter("select", IDENTITY_COLUMNS)
                    parameter("id", eq(user.id))
                }
            }.flatMap { rows ->
                val profile = rows.firstOrNull() ?: return@flatMap AppResult.Success(Unit)
                val patch = missingFields(profile, metadata)
                if (patch.isEmpty()) {
                    AppResult.Success(Unit)
                } else {
                    safeRequest<Unit> {
                        client.patch(postgrest.table("profiles")) {
                            parameter("id", eq(user.id))
                            jsonBody(JsonObject(patch))
                        }
                    }
                }
            }
        }

    private fun missingFields(
        profile: JsonObject,
        metadata: JsonObject,
    ): Map<String, JsonElement> =
        signupMetadataKeys
            .mapNotNull { key ->
                val current = profile[key]
                val incoming = metadata[key] as? JsonPrimitive ?: return@mapNotNull null
                val currentMissing = current == null || current is JsonNull
                val usable = if (key == "age") incoming.intOrNull != null else incoming.isString && incoming.content.isNotBlank()
                if (currentMissing && usable) key to incoming else null
            }.toMap()
}

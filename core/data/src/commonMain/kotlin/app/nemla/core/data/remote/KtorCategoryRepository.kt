package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.network.safeRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal class CategoryDto(
    val id: String,
    val nom: String,
    val couleur: String = DEFAULT_COLOR,
    val icone: String? = null,
    val type: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    val ordre: Int = 0,
    val visible: Boolean = true,
    @SerialName("budget_mensuel") val monthlyBudget: JsonElement? = null,
    @SerialName("budget_annuel") val annualBudget: JsonElement? = null,
) {
    fun toDomain(): Category =
        Category(
            id = CategoryId(id),
            name = nom,
            colorHex = couleur,
            icon = CategoryIcon.entries.firstOrNull { it.name.equals(icone, ignoreCase = true) } ?: CategoryIcon.TAG,
            type = if (type == TYPE_FIXED) CategoryType.FIXED else CategoryType.VARIABLE,
            isDefault = isDefault,
            order = ordre,
            visible = visible,
            budget = budgetOf(monthlyBudget.toCents(), annualBudget.toCents()),
        )

    companion object {
        const val DEFAULT_COLOR = "#64748b"
    }
}

@Serializable
internal class CategoryOrderDto(
    val ordre: Int = 0,
)

internal const val TYPE_FIXED = "fixe"
internal const val TYPE_VARIABLE = "variable"

private const val CATEGORY_COLUMNS = "id,nom,couleur,icone,type,is_default,ordre,visible,budget_mensuel,budget_annuel"

internal fun budgetOf(
    monthlyCents: Long?,
    annualCents: Long?,
): CategoryBudget =
    when {
        annualCents != null && monthlyCents == null -> CategoryBudget.Annual(annualCents)
        monthlyCents != null -> CategoryBudget.Monthly(monthlyCents)
        else -> CategoryBudget.None
    }

internal class KtorCategoryRepository(
    private val postgrest: Postgrest,
) : CategoryRepository {
    private val client get() = postgrest.client

    override suspend fun list(): AppResult<List<Category>> =
        postgrest.withUser { userId ->
            safeRequest<List<CategoryDto>> {
                client.get(postgrest.table("categories")) {
                    parameter("select", CATEGORY_COLUMNS)
                    parameter("user_id", eq(userId))
                    parameter("order", "ordre.asc,nom.asc")
                }
            }.flatMap { rows -> AppResult.Success(rows.map(CategoryDto::toDomain)) }
        }

    override suspend fun create(category: NewCategory): AppResult<Category> =
        postgrest.withUser { userId ->
            nextOrder(userId).flatMap { order ->
                safeRequest<List<CategoryDto>> {
                    client.post(postgrest.table("categories")) {
                        parameter("select", CATEGORY_COLUMNS)
                        returnRepresentation()
                        jsonBody(
                            buildJsonObject {
                                put("user_id", userId)
                                put("nom", category.name.trim())
                                put("couleur", category.colorHex)
                                put("type", if (category.type == CategoryType.FIXED) TYPE_FIXED else TYPE_VARIABLE)
                                put("is_default", false)
                                put("ordre", order)
                            },
                        )
                    }
                }.flatMap { rows ->
                    rows.firstOrNull()?.let { AppResult.Success(it.toDomain()) } ?: AppResult.Failure(AppError.Server(null))
                }
            }
        }

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> {
        val body = update.toPatch()
        if (body.isEmpty()) return AppResult.Success(Unit)
        return postgrest.withUser { userId ->
            safeRequest<Unit> {
                client.patch(postgrest.table("categories")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                    jsonBody(body)
                }
            }
        }
    }

    override suspend fun delete(id: CategoryId): AppResult<Unit> =
        postgrest.withUser { userId ->
            safeRequest<List<CategoryOrderDto>> {
                client.delete(postgrest.table("categories")) {
                    parameter("id", eq(id.value))
                    parameter("user_id", eq(userId))
                    parameter("is_default", "eq.false")
                    parameter("select", "ordre")
                    returnRepresentation()
                }
            }.flatMap { rows -> if (rows.isEmpty()) AppResult.Failure(AppError.NotFound) else AppResult.Success(Unit) }
        }

    private suspend fun nextOrder(userId: String): AppResult<Int> =
        safeRequest<List<CategoryOrderDto>> {
            client.get(postgrest.table("categories")) {
                parameter("select", "ordre")
                parameter("user_id", eq(userId))
                parameter("order", "ordre.desc")
                parameter("limit", 1)
            }
        }.flatMap { rows -> AppResult.Success((rows.firstOrNull()?.ordre ?: 0) + 1) }
}

internal fun CategoryUpdate.toPatch(): JsonObject =
    buildJsonObject {
        name?.let { put("nom", it.trim()) }
        visible?.let { put("visible", it) }
        when (val target = budget) {
            null -> {
                Unit
            }

            CategoryBudget.None -> {
                put("budget_mensuel", centsToJson(null))
                put("budget_annuel", centsToJson(null))
            }

            is CategoryBudget.Monthly -> {
                put("budget_mensuel", centsToJson(target.cents))
                put("budget_annuel", centsToJson(null))
            }

            is CategoryBudget.Annual -> {
                put("budget_mensuel", centsToJson(null))
                put("budget_annuel", centsToJson(target.cents))
            }
        }
    }

package app.nemla.core.domain.model

enum class CategoryType { FIXED, VARIABLE }

enum class CategoryIcon { SHOPPING, UTENSILS, BUS, HOME, HEART, GAMEPAD, SHIRT, REPEAT, MORE, PIGGY, TAG }

sealed interface CategoryBudget {
    data object None : CategoryBudget

    data class Monthly(
        val cents: Long,
    ) : CategoryBudget

    data class Annual(
        val cents: Long,
    ) : CategoryBudget
}

data class Category(
    val id: CategoryId,
    val name: String,
    val colorHex: String,
    val icon: CategoryIcon,
    val type: CategoryType,
    val isDefault: Boolean,
    val order: Int,
    val visible: Boolean,
    val budget: CategoryBudget,
)

data class NewCategory(
    val name: String,
    val colorHex: String,
    val type: CategoryType,
)

data class CategoryUpdate(
    val name: String? = null,
    val visible: Boolean? = null,
    val budget: CategoryBudget? = null,
)

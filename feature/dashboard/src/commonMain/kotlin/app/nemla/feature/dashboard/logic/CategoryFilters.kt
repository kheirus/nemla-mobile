package app.nemla.feature.dashboard.logic

import app.nemla.core.domain.model.CategoryId

internal object CategoryFilters {
    fun isAll(
        filter: Set<CategoryId>?,
        ids: Collection<CategoryId>,
    ): Boolean = filter == null || ids.all { it in filter }

    fun accepts(
        filter: Set<CategoryId>?,
        ids: Collection<CategoryId>,
        categoryId: CategoryId?,
    ): Boolean = isAll(filter, ids) || (categoryId != null && categoryId in filter.orEmpty())

    fun toggle(
        filter: Set<CategoryId>?,
        id: CategoryId,
        ids: Collection<CategoryId>,
    ): Set<CategoryId> {
        val current = filter ?: ids.toSet()
        return if (id in current) current - id else current + id
    }

    fun toggleAll(
        filter: Set<CategoryId>?,
        ids: Collection<CategoryId>,
    ): Set<CategoryId>? = if (isAll(filter, ids)) emptySet() else null

    fun withNewCategories(
        filter: Set<CategoryId>?,
        previousIds: Collection<CategoryId>,
        ids: Collection<CategoryId>,
    ): Set<CategoryId>? = filter?.let { it + ids.filterNot { id -> id in previousIds } }

    fun selectedCount(
        filter: Set<CategoryId>?,
        ids: Collection<CategoryId>,
    ): Int = if (filter == null) ids.size else ids.count { it in filter }
}

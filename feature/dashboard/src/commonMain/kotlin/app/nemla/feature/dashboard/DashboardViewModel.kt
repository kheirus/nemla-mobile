package app.nemla.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.usecase.BootstrapAndLoadProfile
import app.nemla.core.domain.usecase.DeleteExpense
import app.nemla.core.domain.usecase.GetDeferredDebit
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListExpenses
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.MaterializeRecurringCharges
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.SearchExpensesByDate
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.UpdateExpense
import app.nemla.feature.dashboard.logic.CategoryFilters
import app.nemla.feature.dashboard.logic.DashboardCalculator
import app.nemla.feature.dashboard.logic.DashboardInputs
import app.nemla.feature.dashboard.logic.PeriodPages
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

private enum class LoadMode { INITIAL, SILENT, PULL, RETRY }

private class LoadResults(
    val profile: AppResult<UserProfile>,
    val categories: AppResult<List<Category>>,
    val recurring: AppResult<List<RecurringCharge>>,
    val debit: DeferredDebit,
    val pending: AppResult<List<Expense>>,
    val month: AppResult<List<Expense>>,
    val year: AppResult<List<Expense>>,
) {
    fun firstError(): AppError? =
        listOf(profile, categories, recurring, pending, month, year)
            .firstNotNullOfOrNull { (it as? AppResult.Failure)?.error }
}

internal class DashboardViewModel(
    private val loadProfile: BootstrapAndLoadProfile,
    private val listCategories: ListCategories,
    private val listExpenses: ListExpenses,
    private val searchByDate: SearchExpensesByDate,
    private val listRecurring: ListRecurringCharges,
    private val materialize: MaterializeRecurringCharges,
    private val getDeferredDebit: GetDeferredDebit,
    private val updateExpense: UpdateExpense,
    private val deleteExpense: DeleteExpense,
    private val signOut: SignOut,
    private val observeAuthState: ObserveAuthState,
    private val clock: DashboardClock,
    private val language: UiLanguage,
) : ViewModel() {
    private var today: LocalDate = clock.today()
    private var profile: UserProfile? = null
    private var categories: List<Category> = emptyList()
    private var recurring: List<RecurringCharge> = emptyList()
    private var debit: DeferredDebit = DeferredDebit.Immediate
    private var pending: List<Expense> = emptyList()
    private var currentMonth: List<Expense> = emptyList()
    private val years = mutableMapOf<Int, List<Expense>>()
    private var searchResults: List<Expense> = emptyList()
    private var filter: Set<CategoryId>? = null
    private var shownCursor: PeriodCursor = PeriodCursor.monthOf(today)
    private var todayPage = 0
    private var started = false
    private var loadJob: Job? = null
    private var yearJob: Job? = null
    private var searchJob: Job? = null
    private var decimalSeparator = separatorFor(language.current())

    private val mutableState =
        MutableStateFlow(
            DashboardUiState(
                language = language.current(),
                today = today,
                cursor = shownCursor,
                view = DashboardCalculator.compute(DashboardInputs(today = today, cursor = shownCursor)),
            ),
        )
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<DashboardEffect>(Channel.BUFFERED)
    val effects: Flow<DashboardEffect> = effectChannel.receiveAsFlow()

    init {
        load(LoadMode.INITIAL)
        viewModelScope.launch {
            observeAuthState().collect { auth ->
                val email = (auth as? AuthState.SignedIn)?.session?.email?.value
                mutableState.update { it.copy(email = email) }
            }
        }
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            DashboardAction.Started -> onStarted()
            DashboardAction.PullToRefresh -> load(LoadMode.PULL)
            DashboardAction.RetryClicked -> load(LoadMode.RETRY)
            is DashboardAction.PeriodKindSelected -> moveTo(mutableState.value.cursor.withKind(action.kind))
            DashboardAction.PreviousPeriodClicked -> moveTo(mutableState.value.cursor.previous())
            DashboardAction.NextPeriodClicked -> moveTo(mutableState.value.cursor.next())
            is DashboardAction.PeriodPageSelected -> moveTo(PeriodPages.cursorAt(action.page, mutableState.value.cursor, today))
            is DashboardAction.ChartMonthTapped -> drillDown(action.monthNumber)
            is DashboardAction.FilterToggled -> changeFilter(CategoryFilters.toggle(filter, action.id, categoryIds()))
            DashboardAction.FilterAllToggled -> changeFilter(CategoryFilters.toggleAll(filter, categoryIds()))
            is DashboardAction.SpendingTabSelected -> mutableState.update { it.copy(spendingTab = action.tab) }
            is DashboardAction.GroupToggled -> toggleGroup(action.key)
            DashboardAction.TodayPreviousPage -> changeTodayPage(-1)
            DashboardAction.TodayNextPage -> changeTodayPage(1)
            is DashboardAction.SearchDateSelected -> search(minOf(action.date, today))
            DashboardAction.SearchCleared -> clearSearch()
            is DashboardAction.PendingGroupClicked -> openPending(action.index)
            is DashboardAction.ExpenseClicked -> openEditor(action.id)
            is DashboardAction.EditorAmountChanged -> editAmount(action.text)
            is DashboardAction.EditorDateSelected -> editDate(action.date)
            is DashboardAction.EditorCategorySelected -> updateEditor { it.copy(categoryId = action.id) }
            is DashboardAction.EditorPaymentSelected -> editPayment(action.method)
            DashboardAction.EditorSaveClicked -> saveEditor()
            DashboardAction.EditorDeleteClicked -> updateEditor { it.copy(confirmingDelete = true, error = null) }
            DashboardAction.EditorDeleteCancelled -> updateEditor { it.copy(confirmingDelete = false) }
            DashboardAction.EditorDeleteConfirmed -> deleteEdited()
            DashboardAction.EditorDismissed -> dismissEditor()
            DashboardAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
            DashboardAction.SignOutClicked -> signOutOnce()
            DashboardAction.ManageAccountClicked -> openSettings(categories = false)
            DashboardAction.AdjustBudgetsClicked -> openSettings(categories = true)
        }
    }

    private fun onStarted() {
        if (!started) {
            started = true
            return
        }
        load(LoadMode.SILENT)
    }

    private fun openSettings(categories: Boolean) {
        viewModelScope.launch { effectChannel.send(DashboardEffect.OpenSettings(categories)) }
    }

    private fun refreshLanguage() {
        val current = language.current()
        decimalSeparator = separatorFor(current)
        if (mutableState.value.language != current) mutableState.update { it.copy(language = current) }
    }

    private fun load(mode: LoadMode) {
        refreshLanguage()
        loadJob?.cancel()
        yearJob?.cancel()
        mutableState.update {
            it.copy(
                isRefreshing = mode == LoadMode.PULL,
                isInitialLoading = it.isInitialLoading || mode == LoadMode.INITIAL,
                loadError = if (mode == LoadMode.RETRY) null else it.loadError,
            )
        }
        loadJob =
            viewModelScope.launch {
                today = clock.today()
                val year = mutableState.value.cursor.year
                val results = fetchAll(year)
                apply(results, year)
                mutableState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        isPeriodLoading = false,
                        loadError = results.firstError()?.toDashboardError(),
                    )
                }
                recompute()
                mutableState.value.search.date
                    ?.let(::search)
                ensureYear(afterLoad = true)
            }
    }

    private suspend fun fetchAll(year: Int): LoadResults =
        coroutineScope {
            val month = today.yearMonth
            val profileResult = async { loadProfile() }
            val categoriesResult = async { listCategories() }
            val recurringResult = async { listRecurring() }
            val debitResult = async { getDeferredDebit() }
            val pendingResult = async { listExpenses(ExpenseQuery(confirmed = false)) }
            val monthResult = async { listExpenses(ExpenseQuery(dateFrom = month.firstDay, dateTo = month.lastDay, confirmed = true)) }
            val yearResult = async { fetchYear(year) }
            LoadResults(
                profile = profileResult.await(),
                categories = categoriesResult.await(),
                recurring = recurringResult.await(),
                debit = debitResult.await(),
                pending = pendingResult.await(),
                month = monthResult.await(),
                year = yearResult.await(),
            )
        }

    private suspend fun fetchYear(year: Int): AppResult<List<Expense>> {
        materialize(year)
        return listExpenses(ExpenseQuery(dateFrom = LocalDate(year, 1, 1), dateTo = LocalDate(year, 12, 31), confirmed = true))
    }

    private fun apply(
        results: LoadResults,
        year: Int,
    ) {
        results.profile.success()?.let { profile = it }
        results.categories.success()?.let { loaded ->
            filter = CategoryFilters.withNewCategories(filter, categoryIds(), loaded.map { it.id })
            categories = loaded
        }
        results.recurring.success()?.let { recurring = it }
        debit = results.debit
        results.pending.success()?.let { pending = it }
        results.month.success()?.let { currentMonth = it }
        results.year.success()?.let { loaded ->
            years.clear()
            years[year] = loaded
        }
    }

    private fun moveTo(cursor: PeriodCursor) {
        val bounded = PeriodPages.clamp(cursor, today)
        if (bounded == mutableState.value.cursor) return
        mutableState.update { it.copy(cursor = bounded) }
        ensureYear()
    }

    private fun drillDown(monthNumber: Int) {
        val target = mutableState.value.cursor.drillDown(monthNumber)
        if (PeriodPages.isWithinBounds(target, today)) moveTo(target)
    }

    private fun ensureYear(afterLoad: Boolean = false) {
        val cursor = mutableState.value.cursor
        if (years.containsKey(cursor.year)) {
            yearJob?.cancel()
            mutableState.update { it.copy(isPeriodLoading = false) }
            recompute()
            return
        }
        if (!afterLoad && loadJob?.isActive == true) {
            recompute()
            return
        }
        yearJob?.cancel()
        mutableState.update { it.copy(isPeriodLoading = true) }
        recompute()
        yearJob =
            viewModelScope.launch {
                val result = fetchYear(cursor.year)
                when (result) {
                    is AppResult.Success -> years[cursor.year] = result.value
                    is AppResult.Failure -> mutableState.update { it.copy(loadError = result.error.toDashboardError()) }
                }
                mutableState.update { it.copy(isPeriodLoading = false) }
                recompute()
            }
    }

    private fun recompute() {
        val requested = mutableState.value.cursor
        if (years.containsKey(requested.year)) shownCursor = requested
        val view =
            DashboardCalculator.compute(
                DashboardInputs(
                    today = today,
                    cursor = shownCursor,
                    categories = categories,
                    yearExpenses = years[shownCursor.year].orEmpty(),
                    currentMonthExpenses = currentMonth,
                    pendingExpenses = pending,
                    recurring = recurring,
                    budget = profile?.budget ?: GlobalBudget(0, null, BudgetPeriod.MONTH),
                    filter = filter,
                    deferredDebit = debit,
                    todayPage = todayPage,
                ),
            )
        mutableState.update { state ->
            state.copy(
                today = today,
                view = view,
                currency = profile?.currency ?: state.currency,
                pseudo = profile?.pseudo?.trim()?.takeIf { it.isNotEmpty() },
                household = profile?.household,
                categories = categories.map { CategoryChoice(it.id, it.name, it.colorHex) },
                search =
                    state.search.copy(
                        results = state.search.results?.let { DashboardCalculator.rows(searchResults, categories) },
                    ),
            )
        }
    }

    private fun changeFilter(next: Set<CategoryId>?) {
        filter = next
        recompute()
    }

    private fun categoryIds(): List<CategoryId> = categories.map { it.id }

    private fun toggleGroup(key: String) {
        mutableState.update { it.copy(expandedGroup = if (it.expandedGroup == key) null else key) }
    }

    private fun changeTodayPage(delta: Int) {
        val current = mutableState.value.view.today
        todayPage = (current.currentPage + delta).coerceIn(0, current.pageCount - 1)
        recompute()
    }

    private fun search(date: LocalDate) {
        searchJob?.cancel()
        mutableState.update { it.copy(search = it.search.copy(date = date, isLoading = true, failed = false)) }
        searchJob =
            viewModelScope.launch {
                when (val result = searchByDate(date)) {
                    is AppResult.Success -> {
                        searchResults = result.value
                        mutableState.update {
                            it.copy(
                                search =
                                    it.search.copy(
                                        isLoading = false,
                                        results = DashboardCalculator.rows(result.value, categories),
                                    ),
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        searchResults = emptyList()
                        mutableState.update { it.copy(search = it.search.copy(isLoading = false, results = null, failed = true)) }
                    }
                }
            }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        searchResults = emptyList()
        mutableState.update { it.copy(search = SearchUiState()) }
    }

    private fun openPending(index: Int) {
        val group =
            mutableState.value.view.pending
                .getOrNull(index) ?: return
        viewModelScope.launch { effectChannel.send(DashboardEffect.OpenReview(group.review)) }
    }

    private fun knownExpense(id: ExpenseId): Expense? =
        sequenceOf(searchResults, currentMonth)
            .plus(years.values.asSequence())
            .flatten()
            .firstOrNull { it.id == id }

    private fun openEditor(id: ExpenseId) {
        if (mutableState.value.editor != null) return
        val expense = knownExpense(id) ?: return
        val known = categoryIds()
        mutableState.update {
            it.copy(
                editor =
                    ExpenseEditorState(
                        id = expense.id,
                        label = expense.label,
                        source = expense.source,
                        amountText = AmountInput.format(expense.amountCents, decimalSeparator),
                        date = expense.date,
                        categoryId = expense.categoryId?.takeIf { category -> category in known },
                        paymentMethod = expense.paymentMethod,
                        debitDate = debitHint(expense.date, expense.paymentMethod),
                    ),
            )
        }
    }

    private fun debitHint(
        date: LocalDate,
        method: PaymentMethod,
    ): LocalDate? = if (method == PaymentMethod.CARD && debit.enabled) debit.debitDateOf(date) else null

    private fun updateEditor(transform: (ExpenseEditorState) -> ExpenseEditorState) {
        mutableState.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.isBusy) state else state.copy(editor = transform(editor))
        }
    }

    private fun editAmount(text: String) {
        if (!AmountInput.isEditable(text)) return
        updateEditor { it.copy(amountText = text, amountInvalid = false) }
    }

    private fun editDate(date: LocalDate) {
        val clamped = minOf(date, today)
        updateEditor { it.copy(date = clamped, debitDate = debitHint(clamped, it.paymentMethod)) }
    }

    private fun editPayment(method: PaymentMethod) {
        updateEditor { it.copy(paymentMethod = method, debitDate = debitHint(it.date, method)) }
    }

    private fun saveEditor() {
        val editor = mutableState.value.editor ?: return
        if (editor.isBusy) return
        val cents = AmountInput.parseCents(editor.amountText)
        if (editor.amountText.isBlank() || cents == null || cents < 0) {
            updateEditor { it.copy(amountInvalid = true) }
            return
        }
        val edit = ExpenseEdit(cents, minOf(editor.date, today), editor.categoryId, editor.paymentMethod)
        mutableState.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            when (val result = updateExpense(editor.id, edit)) {
                is AppResult.Success -> finishEdit(DashboardNotice.EXPENSE_UPDATED)
                is AppResult.Failure -> failEdit(result.error.toEditorError(EditorError.SAVE_FAILED))
            }
        }
    }

    private fun deleteEdited() {
        val editor = mutableState.value.editor ?: return
        if (editor.isBusy) return
        mutableState.update { it.copy(editor = editor.copy(isDeleting = true, confirmingDelete = false, error = null)) }
        viewModelScope.launch {
            when (val result = deleteExpense(editor.id)) {
                is AppResult.Success -> finishEdit(DashboardNotice.EXPENSE_DELETED)
                is AppResult.Failure -> failEdit(result.error.toEditorError(EditorError.DELETE_FAILED))
            }
        }
    }

    private fun finishEdit(notice: DashboardNotice) {
        mutableState.update { it.copy(editor = null, notice = notice) }
        load(LoadMode.SILENT)
    }

    private fun failEdit(error: EditorError) {
        mutableState.update { state ->
            state.copy(editor = state.editor?.copy(isSaving = false, isDeleting = false, error = error))
        }
    }

    private fun dismissEditor() {
        mutableState.update { state -> if (state.editor?.isBusy == true) state else state.copy(editor = null) }
    }

    private fun signOutOnce() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(isSigningOut = true, signOutFailed = false) }
        viewModelScope.launch {
            when (signOut()) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isSigningOut = false) }
                    effectChannel.send(DashboardEffect.SignedOut)
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isSigningOut = false, signOutFailed = true) }
                }
            }
        }
    }
}

private fun separatorFor(language: AppLanguage): Char = if (language == AppLanguage.EN) '.' else ','

private fun <T> AppResult<T>.success(): T? = (this as? AppResult.Success)?.value

private fun AppError.toDashboardError(): DashboardError =
    when (this) {
        AppError.Network.Offline -> DashboardError.OFFLINE
        AppError.Network.Timeout -> DashboardError.TIMEOUT
        else -> DashboardError.GENERIC
    }

private fun AppError.toEditorError(fallback: EditorError): EditorError =
    when (this) {
        AppError.Network.Offline -> EditorError.OFFLINE
        AppError.Network.Timeout -> EditorError.TIMEOUT
        else -> fallback
    }

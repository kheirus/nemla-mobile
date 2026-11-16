package app.nemla.feature.onboarding

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
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
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Household
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
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.usecase.CompleteOnboarding
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.PrepareOnboarding
import app.nemla.core.domain.usecase.SignOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val profiles = FakeProfiles()
    private val categories = FakeCategories()
    private val parser = FakeParser()
    private val charges = FakeCharges()
    private val auth = FakeAuth()
    private val preferences = FakePreferences()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        OnboardingViewModel(
            prepare = PrepareOnboarding(profiles, categories),
            getProfile = GetUserProfile(profiles),
            parseCharges = ParseRecurringCharges(parser),
            complete = CompleteOnboarding(charges, profiles),
            signOut = SignOut(auth),
            languages = CurrentLanguages(preferences) { AppLanguage.FR },
        )

    @Test
    fun bootstrapsThenLoadsSortedCategoriesAndTheProfile() {
        val vm = viewModel()
        assertEquals(1, profiles.bootstraps)
        assertEquals(
            listOf("Abonnements", "Courses"),
            vm.state.value.categories
                .map { it.name },
        )
        assertEquals("Léa", vm.state.value.pseudo)
        assertEquals(Household.COUPLE, vm.state.value.household)
        assertEquals(Currency.CHF, vm.state.value.currency)
    }

    @Test
    fun analysisReplacesTheRowsAndNeedsThreeCharacters() {
        val vm = viewModel()
        vm.onAction(OnboardingAction.TextChanged("ab"))
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertTrue(parser.calls.isEmpty())
        vm.onAction(OnboardingAction.TextChanged("Netflix 15"))
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertEquals(listOf("Netflix 15" to AppLanguage.EN), parser.calls)
        val row =
            vm.state.value.rows
                .single()
        assertEquals("Netflix", row.name)
        assertEquals("15,49", row.amountText)
        assertEquals(CategoryId("c-sub"), row.categoryId)
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test
    fun unknownCategoriesAreDroppedAndEmptyResultsAreAnnounced() {
        parser.result = AppResult.Success(listOf(RecurringChargeDraft("Gym", 3_000, ChargeFrequency.MONTHLY, 3, CategoryId("other"))))
        val vm = viewModel()
        vm.onAction(OnboardingAction.TextChanged("gym 30"))
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertNull(
            vm.state.value.rows
                .single()
                .categoryId,
        )
        parser.result = AppResult.Success(emptyList())
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertEquals(OnboardingNotice.Known(OnboardingMessage.NOTHING_DETECTED), vm.state.value.notice)
        parser.result = AppResult.Failure(AppError.Network.Offline)
        vm.onAction(OnboardingAction.AnalyzeClicked)
        assertEquals(OnboardingNotice.Known(OnboardingMessage.OFFLINE), vm.state.value.notice)
    }

    @Test
    fun dictationIsAppendedThenAnalysedAlone() {
        val vm = viewModel()
        vm.onAction(OnboardingAction.TextChanged("Netflix 15"))
        vm.onAction(OnboardingAction.DictationActiveChanged(true))
        assertEquals(false, vm.state.value.canAnalyze)
        vm.onAction(OnboardingAction.DictationActiveChanged(false))
        vm.onAction(OnboardingAction.Transcribed("loyer 750 le 5"))
        assertEquals("Netflix 15, loyer 750 le 5", vm.state.value.text)
        assertEquals("loyer 750 le 5", parser.calls.single().first)
        vm.onAction(OnboardingAction.DictationMessage("Transcription impossible"))
        assertIs<OnboardingNotice.Dictation>(vm.state.value.notice)
    }

    @Test
    fun rowsCanBeEditedAndRemoved() {
        val vm = viewModel()
        vm.onAction(OnboardingAction.TextChanged("Netflix 15"))
        vm.onAction(OnboardingAction.AnalyzeClicked)
        val key =
            vm.state.value.rows
                .single()
                .key
        vm.onAction(OnboardingAction.RowNameChanged(key, "Netflix Premium"))
        vm.onAction(OnboardingAction.RowAmountChanged(key, "abc"))
        vm.onAction(OnboardingAction.RowAmountChanged(key, "19,99"))
        vm.onAction(OnboardingAction.RowFrequencySelected(key, ChargeFrequency.YEARLY))
        vm.onAction(OnboardingAction.RowCategorySelected(key, CategoryId("c-food")))
        val row =
            vm.state.value.rows
                .single()
        assertEquals("Netflix Premium", row.name)
        assertEquals("19,99", row.amountText)
        assertEquals(ChargeFrequency.YEARLY, row.frequency)
        assertEquals(CategoryId("c-food"), row.categoryId)
        vm.onAction(OnboardingAction.RowRemoved(key))
        assertTrue(
            vm.state.value.rows
                .isEmpty(),
        )
    }

    @Test
    fun savingInsertsChargesThenFinishesOnboarding() =
        runTest {
            val vm = viewModel()
            vm.onAction(OnboardingAction.TextChanged("Netflix 15"))
            vm.onAction(OnboardingAction.AnalyzeClicked)
            vm.onAction(OnboardingAction.BudgetChanged("1800"))
            vm.effects.test {
                vm.onAction(OnboardingAction.SaveClicked)
                assertEquals(OnboardingEffect.Finished, awaitItem())
            }
            assertEquals(1_549, charges.added.single().amountCents)
            assertEquals(listOf(180_000L), profiles.onboardingBudgets)
        }

    @Test
    fun savingWithoutAnythingStillCompletesAndFailuresAreShown() =
        runTest {
            val vm = viewModel()
            profiles.completeResult = AppResult.Failure(AppError.Server(500))
            vm.onAction(OnboardingAction.SaveClicked)
            assertEquals(OnboardingNotice.Known(OnboardingMessage.SAVE_FAILED), vm.state.value.notice)
            assertEquals(listOf(0L), profiles.onboardingBudgets)
            assertEquals(false, vm.state.value.isSaving)
        }

    @Test
    fun skippingOpensTheDashboardWithoutWritingAnything() =
        runTest {
            val vm = viewModel()
            vm.onAction(OnboardingAction.TextChanged("Netflix 15"))
            vm.onAction(OnboardingAction.AnalyzeClicked)
            vm.onAction(OnboardingAction.BudgetChanged("1800"))
            vm.effects.test {
                vm.onAction(OnboardingAction.SkipClicked)
                assertEquals(OnboardingEffect.Finished, awaitItem())
            }
            assertTrue(charges.added.isEmpty())
            assertTrue(profiles.onboardingBudgets.isEmpty())
        }

    @Test
    fun signOutEmitsSignedOut() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(OnboardingAction.SignOutClicked)
                assertEquals(OnboardingEffect.SignedOut, awaitItem())
            }
        }

    @Test
    fun moduleResolvesTheViewModel() {
        val application =
            koinApplication {
                modules(
                    module {
                        single<ProfileRepository> { profiles }
                        single<CategoryRepository> { categories }
                        single<RecurringChargeParser> { parser }
                        single<RecurringChargeRepository> { charges }
                        single<AuthRepository> { auth }
                        single<PreferencesRepository> { preferences }
                        single<DeviceLanguage> { DeviceLanguage { AppLanguage.FR } }
                    },
                    featureOnboardingModule,
                )
            }
        try {
            application.koin.get<OnboardingViewModel>()
        } finally {
            application.close()
        }
    }
}

private fun category(
    id: String,
    name: String,
) = Category(CategoryId(id), name, "#E4572E", CategoryIcon.TAG, CategoryType.VARIABLE, true, 1, true, CategoryBudget.None)

private class FakeProfiles : ProfileRepository {
    var bootstraps = 0
    var completeResult: AppResult<Unit> = AppResult.Success(Unit)
    val onboardingBudgets = mutableListOf<Long>()

    override suspend fun bootstrap(): AppResult<Unit> {
        bootstraps++
        return AppResult.Success(Unit)
    }

    override suspend fun getProfile(): AppResult<UserProfile> =
        AppResult.Success(
            UserProfile(
                UserId("u"),
                " Léa ",
                null,
                null,
                Household.COUPLE,
                Currency.CHF,
                GlobalBudget(0, null, BudgetPeriod.MONTH),
                false,
                true,
            ),
        )

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(true, emptyList()))

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> {
        onboardingBudgets += monthlyBudgetCents
        return completeResult
    }
}

private class FakeCategories : CategoryRepository {
    override suspend fun list(): AppResult<List<Category>> =
        AppResult.Success(listOf(category("c-food", "Courses"), category("c-sub", "Abonnements")))

    override suspend fun create(category: NewCategory): AppResult<Category> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: CategoryId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeParser : RecurringChargeParser {
    var result: AppResult<List<RecurringChargeDraft>> =
        AppResult.Success(listOf(RecurringChargeDraft("Netflix", 1_549, ChargeFrequency.MONTHLY, 5, CategoryId("c-sub"))))
    val calls = mutableListOf<Pair<String, AppLanguage>>()

    override suspend fun parse(
        text: String,
        language: AppLanguage,
    ): AppResult<List<RecurringChargeDraft>> {
        calls += text to language
        return result
    }
}

private class FakeCharges : RecurringChargeRepository {
    val added = mutableListOf<RecurringChargeDraft>()

    override suspend fun list(): AppResult<List<RecurringCharge>> = AppResult.Success(emptyList())

    override suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit> {
        added += drafts
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: RecurringChargeId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakePreferences : PreferencesRepository {
    override val preferences: StateFlow<AppPreferences?> = MutableStateFlow(AppPreferences(dictationLanguage = AppLanguage.EN))

    override suspend fun load(): AppPreferences = preferences.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeAuth : AuthRepository {
    private val session = Session(UserId("u"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedIn(session))

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}

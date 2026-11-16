package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.BudgetRules
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryNames
import app.nemla.core.domain.model.CategoryPalette
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.StartDestination
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.port.SpeechTranscriber
import app.nemla.core.domain.usecase.AddCategory
import app.nemla.core.domain.usecase.CompleteOnboarding
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteCategory
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.ProfileSaveOutcome
import app.nemla.core.domain.usecase.ResolveStartDestination
import app.nemla.core.domain.usecase.SaveDebitSettings
import app.nemla.core.domain.usecase.SaveGlobalBudget
import app.nemla.core.domain.usecase.SaveProfileIdentity
import app.nemla.core.domain.usecase.SaveRecurringCharges
import app.nemla.core.domain.usecase.TranscribeSpeech
import app.nemla.core.domain.usecase.UpdatePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsUseCasesTest {
    private val profiles = RecordingProfiles()
    private val charges = RecordingCharges()

    private val history = OneExpenseHistory()

    private suspend fun resolve() = ResolveStartDestination(profiles, charges, history)()

    @Test
    fun onlyABlankAccountGoesToOnboarding() =
        runTest {
            profiles.profile = AppResult.Success(profile(onboardingDone = false))
            assertEquals(StartDestination.ONBOARDING, resolve())
            assertEquals(1, profiles.bootstraps)
            assertEquals(listOf(ExpenseQuery(limit = 1)), history.queries)
            profiles.profile = AppResult.Success(profile(onboardingDone = true))
            assertEquals(StartDestination.DASHBOARD, resolve())
        }

    @Test
    fun accountsWithAnExpenseAChargeOrABudgetGoToTheDashboard() =
        runTest {
            profiles.profile = AppResult.Success(profile(onboardingDone = false, budgetCents = 150_000))
            assertEquals(StartDestination.DASHBOARD, resolve())
            profiles.profile = AppResult.Success(profile(onboardingDone = false))
            charges.existing += RecurringCharge(RecurringChargeId("r"), "Loyer", 75_000, ChargeFrequency.MONTHLY, 5, null)
            assertEquals(StartDestination.DASHBOARD, resolve())
            charges.existing.clear()
            history.result = AppResult.Success(listOf(history.sample))
            assertEquals(StartDestination.DASHBOARD, resolve())
        }

    @Test
    fun anyErrorGoesToTheDashboard() =
        runTest {
            profiles.profile = AppResult.Failure(AppError.Network.Offline)
            assertEquals(StartDestination.DASHBOARD, resolve())
            profiles.profile = AppResult.Success(profile(onboardingDone = false))
            charges.listResult = AppResult.Failure(AppError.Network.Timeout)
            assertEquals(StartDestination.DASHBOARD, resolve())
            charges.listResult = null
            history.result = AppResult.Failure(AppError.Server(500))
            assertEquals(StartDestination.DASHBOARD, resolve())
        }

    @Test
    fun onboardingSavesChargesThenTheBudgetAndStopsOnFailure() =
        runTest {
            val draft = RecurringChargeDraft(" Netflix ", 1_500, ChargeFrequency.MONTHLY, 40, null)
            assertEquals(AppResult.Success(Unit), CompleteOnboarding(charges, profiles)(listOf(draft), null))
            assertEquals("Netflix", charges.added.single().name)
            assertEquals(28, charges.added.single().dayOfMonth)
            assertEquals(listOf(0L), profiles.onboardingBudgets)

            charges.addResult = AppResult.Failure(AppError.Network.Timeout)
            assertEquals(AppResult.Failure(AppError.Network.Timeout), CompleteOnboarding(charges, profiles)(listOf(draft), 200_000))
            assertEquals(listOf(0L), profiles.onboardingBudgets)

            CompleteOnboarding(charges, profiles)(emptyList(), 200_000)
            assertEquals(listOf(0L, 200_000L), profiles.onboardingBudgets)
        }

    @Test
    fun recurringAnalysisNeedsThreeCharactersAndEmptySavesDoNothing() =
        runTest {
            var calls = 0
            val parse =
                ParseRecurringCharges(
                    object : RecurringChargeParser {
                        override suspend fun parse(
                            text: String,
                            language: AppLanguage,
                        ): AppResult<List<RecurringChargeDraft>> {
                            calls++
                            return AppResult.Success(emptyList())
                        }
                    },
                )
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT)), parse("ab ", AppLanguage.FR))
            assertEquals(0, calls)
            parse("loyer 750", AppLanguage.FR)
            assertEquals(1, calls)
            assertEquals(AppResult.Success(Unit), SaveRecurringCharges(charges)(emptyList()))
            assertTrue(charges.added.isEmpty())
            assertEquals(false, SaveRecurringCharges.isComplete(RecurringChargeDraft(" ", 100, ChargeFrequency.MONTHLY, 1, null)))
            assertEquals(false, SaveRecurringCharges.isComplete(RecurringChargeDraft("Loyer", 0, ChargeFrequency.MONTHLY, 1, null)))
        }

    @Test
    fun blankTranscriptIsNoSpeech() =
        runTest {
            val recording = VoiceRecording(ByteArray(4), 16_000, 1_000)
            val blank = TranscribeSpeech(transcriber("  "))
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.NO_SPEECH_DETECTED)), blank(recording, AppLanguage.FR))
            val spoken = TranscribeSpeech(transcriber(" loyer 750 "))
            assertEquals(AppResult.Success("loyer 750"), spoken(recording, AppLanguage.FR))
        }

    @Test
    fun globalBudgetDerivesTheOtherPeriodAndRefusesZero() =
        runTest {
            assertEquals(GlobalBudget(100_000, 1_200_000, BudgetPeriod.MONTH), BudgetRules.globalBudget(100_000, BudgetPeriod.MONTH))
            assertEquals(GlobalBudget(83_333, 1_000_000, BudgetPeriod.YEAR), BudgetRules.globalBudget(1_000_000, BudgetPeriod.YEAR))
            assertEquals(
                AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT)),
                SaveGlobalBudget(profiles)(0, BudgetPeriod.MONTH),
            )
            SaveGlobalBudget(profiles)(240_000, BudgetPeriod.YEAR)
            assertEquals(GlobalBudget(20_000, 240_000, BudgetPeriod.YEAR), profiles.budgets.single())
        }

    @Test
    fun fixedChargesAreSpreadPerMonth() {
        val list =
            listOf(
                RecurringCharge(RecurringChargeId("1"), "Loyer", 75_000, ChargeFrequency.MONTHLY, 5, null),
                RecurringCharge(RecurringChargeId("2"), "Assurance", 12_000, ChargeFrequency.YEARLY, 1, null),
            )
        assertEquals(76_000, BudgetRules.fixedMonthlyCents(list))
        assertEquals(912_000, BudgetRules.fixedYearlyCents(list))
        assertEquals(CategoryBudget.Annual(500), BudgetRules.categoryBudget(500, BudgetPeriod.YEAR))
        assertEquals(CategoryBudget.None, BudgetRules.categoryBudget(null, BudgetPeriod.MONTH))
        assertEquals(1_200, BudgetRules.categoryEstimate(100, BudgetPeriod.YEAR))
    }

    @Test
    fun takenPseudoBlocksTheSaveAndOffersSuggestions() =
        runTest {
            profiles.pseudo = PseudoAvailability(false, listOf("jane2"))
            val identity = ProfileIdentity(" jane ", 30, Gender.FEMALE, null, Currency.EUR)
            assertEquals(AppResult.Success(ProfileSaveOutcome.PseudoTaken(listOf("jane2"))), SaveProfileIdentity(profiles)(identity))
            assertTrue(profiles.identities.isEmpty())

            profiles.pseudo = PseudoAvailability(true, emptyList())
            assertEquals(AppResult.Success(ProfileSaveOutcome.Saved), SaveProfileIdentity(profiles)(identity))
            assertEquals("jane", profiles.identities.single().pseudo)

            val tooYoung = ProfileIdentity(null, 10, null, null, Currency.EUR)
            assertEquals(
                AppResult.Failure(AppError.Validation(ValidationCode.AGE_OUT_OF_RANGE)),
                SaveProfileIdentity(profiles)(tooYoung),
            )
        }

    @Test
    fun categoryPaletteSkipsUsedColorsThenGeneratesHues() {
        assertEquals("#F2B701", CategoryPalette.pickUnusedColor(listOf("#e4572e")))
        val all = CategoryPalette.colors
        assertEquals("#BA2C2C", CategoryPalette.pickUnusedColor(all))
        assertEquals("#BA2C2C", CategoryPalette.hslToHex(0, 62, 45))
    }

    @Test
    fun categoryNameSuggestionsFollowTheWebTable() {
        assertEquals(listOf("Alimentation", "Supermarché"), CategoryNames.suggestions("Courses"))
        assertEquals(listOf("Santé & bien-être", "Soins"), CategoryNames.suggestions("Santé"))
        assertEquals(listOf("Transport", "Déplacements", "Mobilité"), CategoryNames.suggestions("Transports en commun"))
        assertEquals(emptyList(), CategoryNames.suggestions("Divers"))
        assertEquals(false, CategoryNames.isBudgetable(category("Impôts", CategoryType.VARIABLE)))
        assertEquals(false, CategoryNames.isBudgetable(category("Logement", CategoryType.FIXED)))
        assertEquals(true, CategoryNames.isBudgetable(category("Loisirs", CategoryType.VARIABLE)))
    }

    @Test
    fun categoriesAreCreatedWithAFreeColorAndDefaultsAreProtected() =
        runTest {
            val repository = RecordingCategories()
            AddCategory(repository)("  Vacances ", listOf(category("Courses", CategoryType.VARIABLE)))
            assertEquals(NewCategory("Vacances", "#F2B701", CategoryType.VARIABLE), repository.created.single())
            assertEquals(
                AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT)),
                AddCategory(repository)("  ", emptyList()),
            )
            assertEquals(AppResult.Failure(AppError.Forbidden), DeleteCategory(repository)(category("Courses", CategoryType.VARIABLE)))
            assertTrue(repository.deleted.isEmpty())
        }

    @Test
    fun deferredDebitNeedsABankAndClampsDays() =
        runTest {
            val debits = RecordingDebits()
            val rule = DeferredDebit(true, 40, 31, null)
            assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT)), SaveDebitSettings(debits)(null, rule))
            SaveDebitSettings(debits)("bnp", rule)
            assertEquals("bnp" to DeferredDebit(true, 31, 28, null), debits.saved.single())
            SaveDebitSettings(debits)("bnp", DeferredDebit.Immediate)
            assertEquals(null, debits.saved.last().first)
        }

    @Test
    fun languagesFollowPreferencesThenTheDevice() =
        runTest {
            val store = MemoryPreferences()
            val languages = CurrentLanguages(store) { AppLanguage.EN }
            assertEquals(AppLanguage.EN, languages.ui())
            UpdatePreferences(store).invoke { it.copy(appLanguage = AppLanguage.FR, theme = ThemeMode.DARK) }
            assertEquals(AppLanguage.FR, languages.ui())
            assertEquals(AppLanguage.EN, languages.dictation())
            assertEquals(ThemeMode.DARK, store.preferences.value?.theme)
        }

    private fun transcriber(text: String) =
        object : SpeechTranscriber {
            override suspend fun transcribe(
                recording: VoiceRecording,
                language: AppLanguage,
            ): AppResult<String> = AppResult.Success(text)
        }

    private fun category(
        name: String,
        type: CategoryType,
    ) = Category(CategoryId(name), name, "#E4572E", CategoryIcon.TAG, type, true, 1, true, CategoryBudget.None)

    private fun profile(
        onboardingDone: Boolean,
        budgetCents: Long = 0,
    ) = UserProfile(
        userId = UserId("user-1"),
        pseudo = null,
        age = null,
        gender = null,
        household = null,
        currency = Currency.EUR,
        budget = GlobalBudget(budgetCents, null, BudgetPeriod.MONTH),
        onboardingDone = onboardingDone,
        dailyReminder = true,
    )
}

private class RecordingProfiles : ProfileRepository {
    var profile: AppResult<UserProfile> = AppResult.Failure(AppError.NotFound)
    var pseudo = PseudoAvailability(true, emptyList())
    var bootstraps = 0
    val budgets = mutableListOf<GlobalBudget>()
    val identities = mutableListOf<ProfileIdentity>()
    val onboardingBudgets = mutableListOf<Long>()

    override suspend fun bootstrap(): AppResult<Unit> {
        bootstraps++
        return AppResult.Success(Unit)
    }

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

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> {
        onboardingBudgets += monthlyBudgetCents
        return AppResult.Success(Unit)
    }
}

private class RecordingCharges : RecurringChargeRepository {
    var addResult: AppResult<Unit> = AppResult.Success(Unit)
    val added = mutableListOf<RecurringChargeDraft>()
    val existing = mutableListOf<RecurringCharge>()
    var listResult: AppResult<List<RecurringCharge>>? = null

    override suspend fun list(): AppResult<List<RecurringCharge>> = listResult ?: AppResult.Success(existing.toList())

    override suspend fun addAll(drafts: List<RecurringChargeDraft>): AppResult<Unit> {
        if (addResult is AppResult.Success) added += drafts
        return addResult
    }

    override suspend fun delete(id: RecurringChargeId): AppResult<Unit> = AppResult.Success(Unit)
}

private class RecordingCategories : CategoryRepository {
    val created = mutableListOf<NewCategory>()
    val deleted = mutableListOf<CategoryId>()

    override suspend fun list(): AppResult<List<Category>> = AppResult.Success(emptyList())

    override suspend fun create(category: NewCategory): AppResult<Category> {
        created += category
        return AppResult.Failure(AppError.NotSupportedYet)
    }

    override suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: CategoryId): AppResult<Unit> {
        deleted += id
        return AppResult.Success(Unit)
    }
}

private class RecordingDebits : DeferredDebitRepository {
    val saved = mutableListOf<Pair<String?, DeferredDebit>>()

    override suspend fun current(): AppResult<DeferredDebit> = AppResult.Success(DeferredDebit.Immediate)

    override suspend fun settings(): AppResult<DebitSettings> = AppResult.Success(DebitSettings(null, DeferredDebit.Immediate, false))

    override suspend fun save(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> {
        saved += bankId to rule
        return AppResult.Success(0)
    }
}

private class MemoryPreferences : PreferencesRepository {
    private val state = MutableStateFlow<AppPreferences?>(null)
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default.also { state.value = it }

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

private class OneExpenseHistory : ExpenseRepository {
    val sample =
        Expense(
            ExpenseId("e"),
            null,
            null,
            null,
            1_000,
            LocalDate(2026, 11, 1),
            LocalDate(2026, 11, 1),
            ExpenseSource.MANUAL,
            PaymentMethod.CARD,
            true,
            null,
        )
    var result: AppResult<List<Expense>> = AppResult.Success(emptyList())
    val queries = mutableListOf<ExpenseQuery>()

    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> {
        queries += query
        return result
    }

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: ExpenseId): AppResult<Unit> = AppResult.Success(Unit)
}

package app.nemla.feature.settings

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.usecase.AddCategory
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteAccount
import app.nemla.core.domain.usecase.DeleteCategory
import app.nemla.core.domain.usecase.DeleteRecurringCharge
import app.nemla.core.domain.usecase.GetDebitSettings
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.RevokeAllAiConsents
import app.nemla.core.domain.usecase.SaveDebitSettings
import app.nemla.core.domain.usecase.SaveGlobalBudget
import app.nemla.core.domain.usecase.SaveProfileIdentity
import app.nemla.core.domain.usecase.SaveRecurringCharges
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.SuggestBudget
import app.nemla.core.domain.usecase.UpdateCategory
import app.nemla.core.domain.usecase.UpdateCurrency
import app.nemla.core.domain.usecase.UpdatePreferences
import app.nemla.feature.settings.budget.BudgetAction
import app.nemla.feature.settings.budget.BudgetMessage
import app.nemla.feature.settings.budget.BudgetViewModel
import app.nemla.feature.settings.card.CardAction
import app.nemla.feature.settings.card.CardMessage
import app.nemla.feature.settings.card.CardViewModel
import app.nemla.feature.settings.card.DebitScope
import app.nemla.feature.settings.categories.CategoriesAction
import app.nemla.feature.settings.categories.CategoriesMessage
import app.nemla.feature.settings.categories.CategoriesViewModel
import app.nemla.feature.settings.common.NoticeKind
import app.nemla.feature.settings.credits.CreditsViewModel
import app.nemla.feature.settings.preferences.PreferencesAction
import app.nemla.feature.settings.preferences.PreferencesMessage
import app.nemla.feature.settings.preferences.PreferencesViewModel
import app.nemla.feature.settings.profile.ProfileAction
import app.nemla.feature.settings.profile.ProfileEffect
import app.nemla.feature.settings.profile.ProfileMessage
import app.nemla.feature.settings.profile.ProfileViewModel
import app.nemla.feature.settings.recurring.RecurringAction
import app.nemla.feature.settings.recurring.RecurringMessage
import app.nemla.feature.settings.recurring.RecurringViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsViewModelsTest : MainDispatcherTest() {
    private val profiles = FakeProfiles()
    private val categories = FakeCategories()
    private val charges = FakeCharges()
    private val parser = FakeParser()
    private val advisor = FakeAdvisor()
    private val debits = FakeDebits()
    private val quotas = FakeQuotas()
    private val accounts = FakeAccounts()
    private val preferences = FakePreferences()
    private val consents = FakeConsents()
    private val auth = FakeAuth()
    private val languages = CurrentLanguages(preferences) { AppLanguage.FR }

    @Test
    fun hostSwitchesTabsRefreshesTheProfileAndCloses() =
        runTest {
            val vm = SettingsViewModel(SettingsTab.CATEGORIES, GetUserProfile(profiles), DeleteAccount(accounts), SignOut(auth))
            assertEquals(SettingsTab.CATEGORIES, vm.state.value.tab)
            assertEquals("Léa", vm.state.value.pseudo)
            assertEquals(Household.FAMILY, vm.state.value.household)
            vm.onAction(SettingsAction.TabSelected(SettingsTab.PROFILE))
            assertEquals(SettingsTab.PROFILE, vm.state.value.tab)
            profiles.profile = AppResult.Success(profile(pseudo = "Sam"))
            vm.onAction(SettingsAction.Started)
            assertEquals("Sam", vm.state.value.pseudo)
            vm.effects.test {
                vm.onAction(SettingsAction.BackClicked)
                assertEquals(SettingsEffect.Close, awaitItem())
            }
        }

    @Test
    fun accountDeletionIsConfirmedThenExplainedWhenUnavailable() =
        runTest {
            val vm = SettingsViewModel(SettingsTab.PROFILE, GetUserProfile(profiles), DeleteAccount(accounts), SignOut(auth))
            vm.onAction(SettingsAction.DeleteAccountClicked)
            assertTrue(vm.state.value.isConfirmingDelete)
            vm.onAction(SettingsAction.DeleteAccountCancelled)
            assertFalse(vm.state.value.isConfirmingDelete)
            vm.onAction(SettingsAction.DeleteAccountClicked)
            vm.onAction(SettingsAction.DeleteAccountConfirmed)
            assertEquals(AccountMessage.DELETE_UNAVAILABLE, vm.state.value.accountMessage)
            assertEquals(0, auth.signOuts)
            accounts.deleteResult = AppResult.Success(Unit)
            vm.effects.test {
                vm.onAction(SettingsAction.DeleteAccountConfirmed)
                assertEquals(SettingsEffect.SignedOut, awaitItem())
            }
            assertEquals(1, auth.signOuts)
        }

    @Test
    fun budgetPanelShowsTheSummarySavesAndEstimates() {
        charges.charges += RecurringCharge(RecurringChargeId("r"), "Loyer", 75_000, ChargeFrequency.MONTHLY, 5, null)
        val vm =
            BudgetViewModel(
                GetUserProfile(profiles),
                ListRecurringCharges(charges),
                SuggestBudget(advisor),
                SaveGlobalBudget(profiles),
                languages,
            )
        vm.onAction(BudgetAction.Opened)
        val loaded = vm.state.value
        assertEquals("1500", loaded.amountText)
        assertEquals(75_000, loaded.fixedMonthlyCents)
        assertEquals(75_000, loaded.remainingCents)
        assertTrue(loaded.hasHistory)
        vm.onAction(BudgetAction.PeriodSelected(BudgetPeriod.YEAR))
        vm.onAction(BudgetAction.AmountChanged("12000"))
        assertEquals(100_000, vm.state.value.monthlyCents)
        vm.onAction(BudgetAction.SaveClicked)
        assertEquals(GlobalBudget(100_000, 1_200_000, BudgetPeriod.YEAR), profiles.budgets.single())
        assertEquals(
            BudgetMessage.Saved,
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(BudgetAction.EstimateClicked)
        assertEquals(BudgetPeriod.MONTH, vm.state.value.period)
        assertEquals("1230", vm.state.value.amountText)
        assertEquals(
            BudgetMessage.Estimated(123_000),
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(BudgetAction.AmountChanged(""))
        assertFalse(vm.state.value.canSave)
        advisor.result = AppResult.Success(estimate(monthly = 0, months = 0))
        vm.onAction(BudgetAction.EstimateClicked)
        assertEquals(
            BudgetMessage.NoHistory,
            vm.state.value.notice
                ?.message,
        )
    }

    private fun recurringViewModel() =
        RecurringViewModel(
            GetUserProfile(profiles),
            ListCategories(categories),
            ListRecurringCharges(charges),
            ParseRecurringCharges(parser),
            SaveRecurringCharges(charges),
            DeleteRecurringCharge(charges),
            languages,
        )

    @Test
    fun recurringAnalysisAppendsDraftsAndSavingNeedsCompleteRows() {
        val vm = recurringViewModel()
        vm.onAction(RecurringAction.Opened)
        vm.onAction(RecurringAction.TextChanged("Netflix 15"))
        vm.onAction(RecurringAction.AnalyzeClicked)
        vm.onAction(RecurringAction.AnalyzeClicked)
        assertEquals(2, vm.state.value.drafts.size)
        vm.onAction(RecurringAction.ManualToggled)
        vm.onAction(RecurringAction.BlankRowAdded)
        val blank =
            vm.state.value.drafts
                .last()
        assertEquals(CategoryId("c-food"), blank.categoryId)
        assertFalse(vm.state.value.canSave)
        vm.onAction(RecurringAction.RowNameChanged(blank.key, "Assurance"))
        vm.onAction(RecurringAction.RowAmountChanged(blank.key, "360"))
        vm.onAction(RecurringAction.RowFrequencySelected(blank.key, ChargeFrequency.YEARLY))
        vm.onAction(RecurringAction.RowDayChanged(blank.key, "x"))
        vm.onAction(RecurringAction.RowDayChanged(blank.key, "45"))
        assertTrue(vm.state.value.canSave)
        vm.onAction(RecurringAction.SaveClicked)
        assertEquals(3, charges.added.size)
        assertEquals(28, charges.added.last().dayOfMonth)
        assertTrue(
            vm.state.value.drafts
                .isEmpty(),
        )
        assertEquals(3, vm.state.value.charges.size)
        assertEquals(
            RecurringMessage.Saved,
            vm.state.value.notice
                ?.message,
        )
        assertEquals(1_549 * 2 + 3_000, vm.state.value.monthlyTotalCents)
    }

    @Test
    fun recurringDictationEmptyResultsPaginationAndDeletion() {
        repeat(7) { charges.charges += RecurringCharge(RecurringChargeId("r$it"), "C$it", 1_000, ChargeFrequency.MONTHLY, 1, null) }
        val vm = recurringViewModel()
        vm.onAction(RecurringAction.Opened)
        assertEquals(2, vm.state.value.pageCount)
        assertEquals(6, vm.state.value.visibleCharges.size)
        vm.onAction(RecurringAction.NextPage)
        assertEquals(1, vm.state.value.visibleCharges.size)
        vm.onAction(RecurringAction.DeleteClicked(RecurringChargeId("r6")))
        assertEquals(0, vm.state.value.page)
        assertEquals(
            RecurringMessage.Deleted,
            vm.state.value.notice
                ?.message,
        )
        parser.result = AppResult.Success(emptyList())
        vm.onAction(RecurringAction.Transcribed("rien du tout"))
        assertEquals("rien du tout", vm.state.value.text)
        assertEquals(
            RecurringMessage.NothingDetected,
            vm.state.value.notice
                ?.message,
        )
        assertEquals(
            NoticeKind.INFO,
            vm.state.value.notice
                ?.kind,
        )
        vm.onAction(RecurringAction.DictationMessage("Transcription impossible"))
        assertIs<RecurringMessage.Dictation>(
            vm.state.value.notice
                ?.message,
        )
    }

    @Test
    fun profileEditSavesOrOffersPseudoSuggestions() =
        runTest {
            val vm = ProfileViewModel(GetUserProfile(profiles), SaveProfileIdentity(profiles))
            vm.onAction(ProfileAction.Opened)
            assertEquals("Léa", vm.state.value.saved.pseudo)
            assertEquals("34", vm.state.value.saved.ageText)
            vm.onAction(ProfileAction.EditClicked)
            vm.onAction(ProfileAction.PseudoChanged("jane"))
            profiles.pseudo =
                app.nemla.core.domain.model
                    .PseudoAvailability(false, listOf("jane2"))
            vm.onAction(ProfileAction.SaveClicked)
            assertEquals(
                ProfileMessage.PSEUDO_TAKEN,
                vm.state.value.notice
                    ?.message,
            )
            assertEquals(listOf("jane2"), vm.state.value.suggestions)
            assertTrue(profiles.identities.isEmpty())
            vm.onAction(ProfileAction.SuggestionPicked("jane2"))
            profiles.pseudo =
                app.nemla.core.domain.model
                    .PseudoAvailability(true, emptyList())
            vm.onAction(ProfileAction.AgeChanged("9"))
            vm.onAction(ProfileAction.SaveClicked)
            assertEquals(
                ProfileMessage.AGE_INVALID,
                vm.state.value.notice
                    ?.message,
            )
            vm.onAction(ProfileAction.AgeChanged("40"))
            vm.effects.test {
                vm.onAction(ProfileAction.SaveClicked)
                assertEquals(ProfileEffect.Saved, awaitItem())
            }
            assertEquals("jane2", profiles.identities.single().pseudo)
            assertEquals(40, profiles.identities.single().age)
            assertEquals("jane2", vm.state.value.saved.pseudo)
        }

    @Test
    fun preferencesUpdateCurrencyLanguagesThemeAndConsent() {
        val vm =
            PreferencesViewModel(
                GetUserProfile(profiles),
                UpdateCurrency(profiles),
                ObservePreferences(preferences),
                UpdatePreferences(preferences),
                languages,
                ObserveAiConsents(consents),
                RevokeAllAiConsents(consents),
            )
        vm.onAction(PreferencesAction.Opened)
        assertTrue(vm.state.value.aiConsentGranted)
        vm.onAction(PreferencesAction.CurrencySelected(Currency.MAD))
        assertEquals(listOf(Currency.MAD), profiles.currencies)
        assertEquals(
            PreferencesMessage.CurrencyUpdated,
            vm.state.value.notice
                ?.message,
        )
        profiles.currencyResult = AppResult.Failure(AppError.Network.Offline)
        vm.onAction(PreferencesAction.CurrencySelected(Currency.USD))
        assertEquals(Currency.MAD, vm.state.value.currency)
        vm.onAction(PreferencesAction.AppLanguageSelected(AppLanguage.EN))
        assertEquals(AppLanguage.EN, preferences.state.value?.appLanguage)
        assertEquals(AppLanguage.EN, vm.state.value.appLanguage)
        vm.onAction(PreferencesAction.DictationLanguageSelected(AppLanguage.EN))
        assertEquals(
            PreferencesMessage.DictationLanguageSet(AppLanguage.EN),
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(PreferencesAction.ThemeSelected(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, vm.state.value.theme)
        vm.onAction(PreferencesAction.RevokeConsentClicked)
        assertFalse(vm.state.value.aiConsentGranted)
    }

    @Test
    fun cardPanelPrefillsBankRulesAndReportsTheScope() {
        val vm = CardViewModel(GetDebitSettings(debits), SaveDebitSettings(debits), languages) { testToday }
        vm.onAction(CardAction.Opened)
        assertEquals(DebitScope.TODAY, vm.state.value.scope)
        vm.onAction(CardAction.ModeSelected(true))
        assertFalse(vm.state.value.canSave)
        vm.onAction(CardAction.BankSelected("credit-agricole"))
        assertEquals("25", vm.state.value.cutoffText)
        assertEquals("1", vm.state.value.debitText)
        assertEquals(LocalDate(2026, 12, 1), vm.state.value.exampleDate)
        vm.onAction(CardAction.SaveClicked)
        assertEquals("credit-agricole" to DeferredDebit(true, 25, 1, testToday), debits.saved.single())
        assertEquals(
            CardMessage.AppliedFrom(testToday, 3),
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(CardAction.ScopeSelected(DebitScope.ALL))
        vm.onAction(CardAction.SaveClicked)
        assertEquals(
            CardMessage.AppliedToAll(3),
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(CardAction.DateSelected(LocalDate(2030, 1, 1)))
        assertEquals(testToday, vm.state.value.date)
        vm.onAction(CardAction.ModeSelected(false))
        vm.onAction(CardAction.SaveClicked)
        assertEquals(null to DeferredDebit(false, 25, 1, null), debits.saved.last())
        assertEquals(
            CardMessage.ImmediateSaved(3),
            vm.state.value.notice
                ?.message,
        )
    }

    @Test
    fun cardPanelRestoresAnExistingRule() {
        debits.settings = DebitSettings("sg", DeferredDebit(true, 31, 3, LocalDate(2026, 3, 1)), choiceMade = true)
        val vm = CardViewModel(GetDebitSettings(debits), SaveDebitSettings(debits), languages) { testToday }
        vm.onAction(CardAction.Opened)
        assertEquals(DebitScope.DATE, vm.state.value.scope)
        assertEquals(LocalDate(2026, 3, 1), vm.state.value.date)
        assertEquals("sg", vm.state.value.bankId)
    }

    private fun categoriesViewModel() =
        CategoriesViewModel(
            GetUserProfile(profiles),
            ListCategories(categories),
            SuggestBudget(advisor),
            AddCategory(categories),
            UpdateCategory(categories),
            DeleteCategory(categories),
            languages,
        )

    @Test
    fun categoriesAreListedWithBudgetsEstimatesAndRules() {
        val vm = categoriesViewModel()
        vm.onAction(CategoriesAction.Opened)
        val rows = vm.state.value.rows
        assertEquals(3, rows.size)
        assertEquals(30_000, rows.first { it.id == CategoryId("c-food") }.estimateCents)
        assertFalse(rows.first { it.id == CategoryId("c-rent") }.budgetable)
        val trip = rows.first { it.id == CategoryId("c-trip") }
        assertEquals("1200", trip.budgetText)
        assertEquals(BudgetPeriod.YEAR, trip.period)
        assertEquals(listOf("Alimentation", "Supermarché"), rows.first().suggestions)
    }

    @Test
    fun categoryBudgetIsSavedOnCommitAndPeriodAndEstimate() {
        val vm = categoriesViewModel()
        vm.onAction(CategoriesAction.Opened)
        val food = CategoryId("c-food")
        vm.onAction(CategoriesAction.BudgetCommitted(food))
        assertTrue(categories.updates.isEmpty())
        vm.onAction(CategoriesAction.BudgetChanged(food, "250"))
        vm.onAction(CategoriesAction.BudgetCommitted(food))
        assertEquals(
            food to
                app.nemla.core.domain.model
                    .CategoryUpdate(budget = CategoryBudget.Monthly(25_000)),
            categories.updates.last(),
        )
        vm.onAction(CategoriesAction.PeriodSelected(food, BudgetPeriod.YEAR))
        assertEquals(
            CategoryBudget.Annual(25_000),
            categories.updates
                .last()
                .second.budget,
        )
        vm.onAction(CategoriesAction.EstimateClicked(food))
        assertEquals(
            "3600",
            vm.state.value.rows
                .first { it.id == food }
                .budgetText,
        )
        assertEquals(
            CategoryBudget.Annual(360_000),
            categories.updates
                .last()
                .second.budget,
        )
        vm.onAction(CategoriesAction.BudgetChanged(food, ""))
        vm.onAction(CategoriesAction.BudgetCommitted(food))
        assertEquals(
            CategoryBudget.None,
            categories.updates
                .last()
                .second.budget,
        )
    }

    @Test
    fun categoriesCanBeAddedRenamedHiddenAndDeleted() {
        val vm = categoriesViewModel()
        vm.onAction(CategoriesAction.Opened)
        vm.onAction(CategoriesAction.NewNameChanged("  "))
        vm.onAction(CategoriesAction.AddClicked)
        assertTrue(categories.created.isEmpty())
        vm.onAction(CategoriesAction.NewNameChanged("Animaux"))
        vm.onAction(CategoriesAction.AddClicked)
        assertEquals("#F2B701", categories.created.single().colorHex)
        assertEquals(4, vm.state.value.rows.size)
        assertEquals(
            CategoriesMessage.ADDED,
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(CategoriesAction.RenameClicked(CategoryId("c-food"), "Alimentation"))
        assertEquals(
            "Alimentation",
            vm.state.value.rows
                .first()
                .category.name,
        )
        categories.updateResult = AppResult.Failure(AppError.Server(500))
        vm.onAction(CategoriesAction.VisibilityToggled(CategoryId("c-food"), false))
        assertTrue(
            vm.state.value.rows
                .first()
                .category.visible,
        )
        assertEquals(
            CategoriesMessage.FAILED,
            vm.state.value.notice
                ?.message,
        )
        vm.onAction(CategoriesAction.DeleteClicked(CategoryId("c-food")))
        assertTrue(categories.deleted.isEmpty())
        vm.onAction(CategoriesAction.DeleteClicked(CategoryId("c-trip")))
        assertEquals(listOf(CategoryId("c-trip")), categories.deleted)
        assertEquals(
            CategoriesMessage.DELETED,
            vm.state.value.notice
                ?.message,
        )
    }

    @Test
    fun creditsShowTheQuotaOrAFailure() {
        val vm = CreditsViewModel(GetMonthlyQuota(quotas))
        assertTrue(vm.state.value.isLoading)
        vm.refresh()
        assertEquals(
            10,
            vm.state.value.quota
                ?.voiceRemaining,
        )
        val failing = CreditsViewModel(GetMonthlyQuota(FakeQuotas().apply { result = AppResult.Failure(AppError.Network.Offline) }))
        failing.refresh()
        assertTrue(failing.state.value.failed)
    }

    @Test
    fun moduleResolvesEveryViewModel() {
        val application =
            koinApplication {
                modules(
                    module {
                        single<ProfileRepository> { profiles }
                        single<CategoryRepository> { categories }
                        single<RecurringChargeRepository> { charges }
                        single<RecurringChargeParser> { parser }
                        single<BudgetAdvisor> { advisor }
                        single<DeferredDebitRepository> { debits }
                        single<QuotaRepository> { quotas }
                        single<AccountRepository> { accounts }
                        single<PreferencesRepository> { preferences }
                        single<AiConsentRepository> { consents }
                        single<AuthRepository> { auth }
                        single<DeviceLanguage> { DeviceLanguage { AppLanguage.FR } }
                    },
                    featureSettingsModule,
                )
            }
        try {
            val koin = application.koin
            assertEquals(
                SettingsTab.CREDITS,
                koin
                    .get<SettingsViewModel> { parametersOf(SettingsTab.CREDITS) }
                    .state.value.tab,
            )
            koin.get<BudgetViewModel>()
            koin.get<RecurringViewModel>()
            koin.get<ProfileViewModel>()
            koin.get<PreferencesViewModel>()
            koin.get<CardViewModel>()
            koin.get<CreditsViewModel>()
            koin.get<CategoriesViewModel>()
        } finally {
            application.close()
        }
    }
}

package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.data.remote.PREFER_HEADER
import app.nemla.core.data.remote.centsToJson
import app.nemla.core.data.remote.decimalToCents
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryIcon
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgrestRepositoriesTest {
    @Test
    fun profileIsReadAndMapped() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json(
                        """[{"monthly_budget":1234.5,"annual_budget":null,"budget_periode":"annee","pseudo":"jane","sexe":"homme",
                            "age":41,"situation_familiale":"famille","daily_reminder":false,"devise":"CHF","onboarding_done":true}]""",
                    )
                }
            val profile = harness.profiles.getProfile().getOrNull()!!
            assertEquals("jane", profile.pseudo)
            assertEquals(41, profile.age)
            assertEquals(Gender.MALE, profile.gender)
            assertEquals(Household.FAMILY, profile.household)
            assertEquals(Currency.CHF, profile.currency)
            assertEquals(GlobalBudget(123_450, null, BudgetPeriod.YEAR), profile.budget)
            assertEquals(true, profile.onboardingDone)
            assertEquals(false, profile.dailyReminder)
            val request = harness.requests.single()
            assertEquals("/rest/v1/profiles", request.path)
            assertEquals("eq.$USER_ID", request.query("id"))
            assertTrue(request.query("select").orEmpty().contains("monthly_budget"))
        }

    @Test
    fun missingProfileIsNotFoundAndSignedOutIsUnauthorized() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppError.NotFound, harness.profiles.getProfile().failure)
            val signedOut = DataHarness { json("[]") }
            assertEquals(AppError.Unauthorized, signedOut.profiles.getProfile().failure)
            assertTrue(signedOut.requests.isEmpty())
        }

    @Test
    fun budgetIsWrittenWithExactDecimals() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            val result = harness.profiles.updateBudget(GlobalBudget(123_450, null, BudgetPeriod.MONTH))
            assertEquals(AppResult.Success(Unit), result)
            val request = harness.requests.single()
            assertEquals("PATCH", request.method)
            assertEquals("eq.$USER_ID", request.query("id"))
            assertEquals("""{"monthly_budget":1234.50,"annual_budget":null,"budget_periode":"mois"}""", request.body)
        }

    @Test
    fun identityUpdateSendsExplicitNulls() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            harness.profiles.updateIdentity(
                ProfileIdentity(pseudo = "  ", age = null, gender = Gender.FEMALE, household = null, currency = Currency.MAD),
            )
            assertEquals(
                """{"pseudo":null,"sexe":"femme","age":null,"situation_familiale":null,"devise":"MAD"}""",
                harness.requests.single().body,
            )
        }

    @Test
    fun dailyReminderIsPatched() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            harness.profiles.setDailyReminder(false)
            assertEquals("""{"daily_reminder":false}""", harness.requests.single().body)
        }

    @Test
    fun pseudoCheckUsesTheRpcAndIsOptimisticOnError() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("""{"available":false,"suggestions":["jane2","jane3"]}""") }
            assertEquals(PseudoAvailability(false, listOf("jane2", "jane3")), harness.profiles.checkPseudo(" jane ").getOrNull())
            val request = harness.requests.single()
            assertEquals("/rest/v1/rpc/suggest_pseudo", request.path)
            assertEquals("""{"p_pseudo":"jane"}""", request.body)

            val failing = DataHarness(store = signedInStore()) { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }
            assertEquals(PseudoAvailability(true, emptyList()), failing.profiles.checkPseudo("jane").getOrNull())

            val blank = DataHarness(store = signedInStore()) { json("{}") }
            assertEquals(PseudoAvailability(true, emptyList()), blank.profiles.checkPseudo("   ").getOrNull())
            assertTrue(blank.requests.isEmpty())
        }

    @Test
    fun bootstrapCallsTheRpcThenCopiesMissingSignupMetadata() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    when (request.path) {
                        "/rest/v1/rpc/bootstrap_user" -> {
                            noContent()
                        }

                        "/auth/v1/user" -> {
                            json(
                                """{"id":"$USER_ID","user_metadata":{"pseudo":"jane","age":34,"sexe":"femme","situation_familiale":"couple"}}""",
                            )
                        }

                        else -> {
                            if (request.method ==
                                "GET"
                            ) {
                                json("""[{"pseudo":"already","age":null,"sexe":null,"situation_familiale":null}]""")
                            } else {
                                noContent()
                            }
                        }
                    }
                }
            assertEquals(AppResult.Success(Unit), harness.profiles.bootstrap())
            assertEquals("{}", harness.on("POST", "/rest/v1/rpc/bootstrap_user").single().body)
            val patch = harness.on("PATCH", "/rest/v1/profiles").single()
            assertEquals("""{"age":34,"sexe":"femme","situation_familiale":"couple"}""", patch.body)
        }

    @Test
    fun bootstrapIgnoresSyncFailures() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.path == "/rest/v1/rpc/bootstrap_user") noContent() else json("{}", HttpStatusCode.InternalServerError)
                }
            assertEquals(AppResult.Success(Unit), harness.profiles.bootstrap())
        }

    @Test
    fun categoriesAreListedInOrderAndMapped() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json(
                        """[
                          {"id":"c1","nom":"Courses","couleur":"#E4572E","icone":"shopping","type":"variable","is_default":true,"ordre":1,"visible":true,"budget_mensuel":300,"budget_annuel":null},
                          {"id":"c2","nom":"Assurances","couleur":"#6B2737","icone":"Tag","type":"fixe","is_default":false,"ordre":2,"visible":false,"budget_mensuel":null,"budget_annuel":1200.5},
                          {"id":"c3","nom":"Divers","couleur":"#7A7F87","icone":"more","type":"variable","is_default":true,"ordre":3,"visible":true,"budget_mensuel":null,"budget_annuel":null}
                        ]""",
                    )
                }
            val categories = harness.categories.list().getOrNull()!!
            assertEquals(
                Category(
                    CategoryId("c1"),
                    "Courses",
                    "#E4572E",
                    CategoryIcon.SHOPPING,
                    CategoryType.VARIABLE,
                    true,
                    1,
                    true,
                    CategoryBudget.Monthly(30_000),
                ),
                categories[0],
            )
            assertEquals(CategoryIcon.TAG, categories[1].icon)
            assertEquals(CategoryType.FIXED, categories[1].type)
            assertEquals(CategoryBudget.Annual(120_050), categories[1].budget)
            assertEquals(CategoryBudget.None, categories[2].budget)
            val request = harness.requests.single()
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("ordre.asc,nom.asc", request.query("order"))
        }

    @Test
    fun categoryIsCreatedAfterTheLastOne() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.method == "GET") {
                        json("""[{"ordre":13}]""")
                    } else {
                        json(
                            """[{"id":"c9","nom":"Animaux","couleur":"#4361EE","icone":"Tag","type":"variable","is_default":false,"ordre":14}]""",
                        )
                    }
                }
            val created = harness.categories.create(NewCategory(" Animaux ", "#4361EE", CategoryType.VARIABLE)).getOrNull()!!
            assertEquals(CategoryId("c9"), created.id)
            assertEquals(14, created.order)
            val lookup = harness.on("GET", "/rest/v1/categories").single()
            assertEquals("ordre.desc", lookup.query("order"))
            assertEquals("1", lookup.query("limit"))
            val insert = harness.on("POST", "/rest/v1/categories").single()
            assertEquals("return=representation", insert.data.headers[PREFER_HEADER])
            assertEquals(
                """{"user_id":"$USER_ID","nom":"Animaux","couleur":"#4361EE","type":"variable","is_default":false,"ordre":14}""",
                insert.body,
            )
        }

    @Test
    fun categoryUpdateSendsOnlyChangedFields() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            harness.categories.update(CategoryId("c2"), CategoryUpdate(budget = CategoryBudget.Annual(60_000)))
            harness.categories.update(CategoryId("c2"), CategoryUpdate(name = "Maison", visible = false, budget = CategoryBudget.None))
            assertEquals(AppResult.Success(Unit), harness.categories.update(CategoryId("c2"), CategoryUpdate()))
            assertEquals(
                listOf(
                    """{"budget_mensuel":null,"budget_annuel":600.00}""",
                    """{"nom":"Maison","visible":false,"budget_mensuel":null,"budget_annuel":null}""",
                ),
                harness.requests.map { it.body },
            )
            assertTrue(harness.requests.all { it.query("id") == "eq.c2" && it.query("user_id") == "eq.$USER_ID" })
        }

    @Test
    fun defaultCategoriesCannotBeDeleted() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { json("[]") }
            assertEquals(AppError.NotFound, harness.categories.delete(CategoryId("c1")).failure)
            val request = harness.requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("eq.false", request.query("is_default"))
            val deleting = DataHarness(store = signedInStore()) { json("""[{"ordre":4}]""") }
            assertEquals(AppResult.Success(Unit), deleting.categories.delete(CategoryId("c4")))
        }

    @Test
    fun quotaIsReadFromTheRpcInBothShapes() =
        runTest {
            val row =
                """{"id":"q","user_id":"$USER_ID","month":"2026-11","voice_count":4,""" +
                    """"voice_limit":30,"import_count":1,"import_limit":1}"""
            val expected = MonthlyQuota(YearMonth(2026, 11), 4, 30, 1, 1)
            val single = DataHarness(store = signedInStore()) { json(row) }
            assertEquals(expected, single.quotas.currentMonth().getOrNull())
            assertEquals("/rest/v1/rpc/ensure_month_quota", single.requests.single().path)
            val array = DataHarness(store = signedInStore()) { json("[$row]") }
            assertEquals(expected, array.quotas.currentMonth().getOrNull())
            val broken = DataHarness(store = signedInStore()) { json("""{"month":"novembre"}""") }
            assertEquals(AppError.Server(null), broken.quotas.currentMonth().failure)
        }

    @Test
    fun decimalsRoundTripWithoutFloatingPoint() {
        assertEquals(29, decimalToCents("0.29"))
        assertEquals(123_450, decimalToCents("1234.5"))
        assertEquals(1_235, decimalToCents("12.345"))
        assertEquals("0.29", centsToJson(29).toString())
        assertEquals("-12.05", centsToJson(-1_205).toString())
        assertEquals("null", centsToJson(null).toString())
    }
}

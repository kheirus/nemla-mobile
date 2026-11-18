package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.getOrNull
import app.nemla.core.data.notifications.SecureStoreNotificationMemory
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.security.InMemorySecureStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRepositoriesTest {
    private val report =
        """{"month":"2026-10","kind":"mois","previous_month":"2026-09","total":1234.5,"previous_total":1000,"budget":1500,
           "expense_count":12,"categories":[{"nom":"Courses","montant":400.25,"part":32.4,"variation":-12.5}],
           "highlights":[" Moins de restaurants "],"advice":["Gardez le cap"],"demo":true}"""

    @Test
    fun unseenRowsAreReadWithTheWebQuery() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.path == "/rest/v1/profiles") {
                        json("""[{"devise":"CHF","daily_reminder":true}]""")
                    } else {
                        json(
                            """[
                              {"id":"n-1","channel":"in_app","kind":"bilan_mois","title":"Bilan","body":"Prêt","periode":"2026-10","payload":$report,"delivered_at":null},
                              {"id":"n-2","channel":"push","kind":"rappel_jour","title":"nemla","body":"Rappel","periode":null,"payload":null,"delivered_at":"2026-11-01T10:00:00Z"}
                            ]""",
                        )
                    }
                }
            val items = harness.inbox.unseen().getOrNull()!!
            val request = harness.on("GET", "/rest/v1/user_notifications").single()
            assertEquals("id,channel,kind,title,body,periode,payload,delivered_at", request.query("select"))
            assertEquals("eq.$USER_ID", request.query("user_id"))
            assertEquals("is.null", request.query("seen_at"))
            assertEquals("created_at.desc", request.query("order"))
            assertEquals(2, items.size)
            val first = items[0]
            assertEquals(InboxItemId("n-1"), first.id)
            assertEquals(InboxChannel.IN_APP, first.channel)
            assertEquals(InboxKind.MONTH_REPORT, first.kind)
            assertEquals(ReviewPeriod.Month(YearMonth(2026, 10)), first.period)
            assertFalse(first.delivered)
            val sheet = assertNotNull(first.report)
            assertEquals(Currency.CHF, sheet.currency)
            assertEquals(123_450, sheet.review.totalCents)
            assertEquals(ReviewPeriod.Month(YearMonth(2026, 9)), sheet.review.previousPeriod)
            assertEquals(
                40_025,
                sheet.review.categories
                    .single()
                    .amountCents,
            )
            assertEquals(
                32,
                sheet.review.categories
                    .single()
                    .sharePercent,
            )
            assertEquals(listOf("Moins de restaurants"), sheet.review.highlights)
            val second = items[1]
            assertEquals(InboxChannel.PUSH, second.channel)
            assertEquals(InboxKind.DAILY_REMINDER, second.kind)
            assertTrue(second.delivered)
            assertNull(second.report)
        }

    @Test
    fun invalidPayloadsAndUnknownRowsNeverFailTheList() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.path == "/rest/v1/profiles") {
                        json("[]")
                    } else {
                        json(
                            """[
                              {"id":"n-1","channel":"in_app","kind":"bilan_mois","title":"A","body":"B","periode":"2026-10","payload":{"month":"2026-10"}},
                              {"id":"n-2","channel":"in_app","kind":"bilan_annee","title":"A","body":"B","periode":"2025","payload":"oops"},
                              {"id":"n-3","channel":"email","kind":"bilan_mois","title":"A","body":"B"},
                              {"id":"n-4","channel":"push","kind":"promo","title":"A","body":"B"},
                              {"channel":"push","kind":"bilan_mois","title":"A","body":"B"},
                              {"id":"n-5","channel":"in_app","kind":"bilan_mois","title":"A","body":"B","periode":"2026-13",
                               "payload":{"month":"2026-13","previous_month":"2026-12","total":1}}
                            ]""",
                        )
                    }
                }
            val items = harness.inbox.unseen().getOrNull()!!
            assertEquals(listOf("n-1", "n-2", "n-5"), items.map { it.id.value })
            assertTrue(items.all { it.report == null })
            assertEquals(ReviewPeriod.Year(2025), items[1].period)
            assertNull(items[2].period)
        }

    @Test
    fun aYearPayloadWithoutKindUsesTheRowKind() =
        runTest {
            val year =
                """{"month":"2025","previous_month":"2024","total":"9000.00","categories":[]}"""
            val harness =
                DataHarness(store = signedInStore()) { request ->
                    if (request.path == "/rest/v1/profiles") {
                        json("""{"message":"boom"}""", HttpStatusCode.InternalServerError)
                    } else {
                        json(
                            """[{"id":"n-1","channel":"in_app","kind":"bilan_annee","title":"A","body":"B","periode":"2025","payload":$year}]""",
                        )
                    }
                }
            val sheet =
                assertNotNull(
                    harness.inbox
                        .unseen()
                        .getOrNull()!!
                        .single()
                        .report,
                )
            assertEquals(ReviewPeriod.Year(2025), sheet.review.period)
            assertEquals(Currency.EUR, sheet.currency)
        }

    @Test
    fun rowsWithoutPayloadDoNotReadTheProfile() =
        runTest {
            val harness =
                DataHarness(store = signedInStore()) {
                    json("""[{"id":"n-1","channel":"push","kind":"rappel_jour","title":"A","body":"B"}]""")
                }
            assertEquals(
                1,
                harness.inbox
                    .unseen()
                    .getOrNull()!!
                    .size,
            )
            assertTrue(harness.on("GET", "/rest/v1/profiles").isEmpty())
        }

    @Test
    fun seenAndDeliveredArePatchedWithTheClock() =
        runTest {
            val harness = DataHarness(store = signedInStore()) { noContent() }
            assertEquals(AppResult.Success(Unit), harness.inbox.markSeen(InboxItemId("n-1")))
            assertEquals(AppResult.Success(Unit), harness.inbox.markDelivered(InboxItemId("n-2")))
            val (seen, delivered) = harness.on("PATCH", "/rest/v1/user_notifications")
            assertEquals("eq.n-1", seen.query("id"))
            assertEquals("eq.$USER_ID", seen.query("user_id"))
            assertEquals("""{"seen_at":"$now"}""", seen.body)
            assertEquals("eq.n-2", delivered.query("id"))
            assertEquals("""{"delivered_at":"$now","seen_at":"$now"}""", delivered.body)
        }

    @Test
    fun signedOutOrFailingInboxReturnsFailures() =
        runTest {
            val signedOut = DataHarness { json("[]") }
            assertEquals(AppError.Unauthorized, signedOut.inbox.unseen().failure)
            assertEquals(AppError.Unauthorized, signedOut.inbox.markSeen(InboxItemId("n-1")).failure)
            assertTrue(signedOut.requests.isEmpty())
            val failing = DataHarness(store = signedInStore()) { json("""{"message":"boom"}""", HttpStatusCode.InternalServerError) }
            assertTrue(failing.inbox.unseen() is AppResult.Failure)
        }

    @Test
    fun memoryKeepsOnlyTheCurrentAndPreviousMonth() =
        runTest {
            val store = InMemorySecureStore()
            val clock = FixedClock(LocalDateTime(2026, 11, 19, 12, 0).toInstant(TimeZone.UTC))
            val memory = SecureStoreNotificationMemory(store, clock, NoOpLogger) { TimeZone.UTC }
            assertEquals(AppResult.Success(false), memory.wasAlerted("2026-11:c-1"))
            memory.remember("2026-09:c-1")
            memory.remember("2026-10:c-2")
            memory.remember("2026-11:c-1")
            assertEquals(AppResult.Success(true), memory.wasAlerted("2026-11:c-1"))
            assertEquals(AppResult.Success(true), memory.wasAlerted("2026-10:c-2"))
            assertEquals(AppResult.Success(false), memory.wasAlerted("2026-09:c-1"))
            assertEquals("""{"alerted":["2026-10:c-2","2026-11:c-1"]}""", store.get(SecureStoreNotificationMemory.KEY).getOrNull())
            clock.instant = LocalDateTime(2027, 1, 2, 9, 0).toInstant(TimeZone.UTC)
            memory.remember("2027-01:c-3")
            assertEquals("""{"alerted":["2027-01:c-3"]}""", store.get(SecureStoreNotificationMemory.KEY).getOrNull())
            assertEquals(AppResult.Success(Unit), memory.clear())
            assertNull(store.get(SecureStoreNotificationMemory.KEY).getOrNull())
        }

    @Test
    fun corruptedMemoryIsTreatedAsEmpty() =
        runTest {
            val store = InMemorySecureStore(mapOf(SecureStoreNotificationMemory.KEY to "not json"))
            val memory = SecureStoreNotificationMemory(store, FixedClock(), NoOpLogger) { TimeZone.UTC }
            assertEquals(AppResult.Success(false), memory.wasAlerted("2026-11:c-1"))
        }
}

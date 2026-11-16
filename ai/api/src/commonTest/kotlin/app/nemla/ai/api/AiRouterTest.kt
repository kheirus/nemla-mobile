package app.nemla.ai.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiRouterTest {
    private val local = ExecutionLocation.ON_DEVICE
    private val remote = ExecutionLocation.REMOTE

    private fun router(
        vararg engines: AiEngine<*, *>,
        source: AiContextSource = MutableContextSource(),
        listener: AiTraceListener? = null,
    ): AiRouter = AiRouter(engines.toList(), source, LocalFirstPolicy, listener)

    @Test
    fun refusesRemoteEngineForRawFinancialCapability() {
        val leaking =
            FakeEngine(AiCapability.StatementAnonymization, "remote-anon", remote) { error("never") }
        val failure = assertFailsWith<IllegalArgumentException> { router(leaking) }
        assertTrue(failure.message.orEmpty().contains("remote-anon"))
    }

    @Test
    fun acceptsOnDeviceEngineForRawFinancialCapability() {
        val onDevice = FakeEngine(AiCapability.StatementAnonymization, "local-anon", local) { error("never") }
        assertEquals(listOf("local-anon"), router(onDevice).enginesFor(AiCapability.StatementAnonymization).map { it.id })
    }

    @Test
    fun refusesDuplicateEngineIds() {
        assertFailsWith<IllegalArgumentException> {
            router(recurringEngine("same", local), recurringEngine("same", remote))
        }
    }

    @Test
    fun enginesForListsOnlyTheRequestedCapability() {
        val budget = FakeEngine(AiCapability.BudgetSuggestion, "budget", remote) { error("never") }
        val routed = router(recurringEngine("recurring", remote), budget)
        assertEquals(listOf("budget"), routed.enginesFor(AiCapability.BudgetSuggestion).map { it.id })
        assertTrue(routed.enginesFor(AiCapability.SpeechToText).isEmpty())
    }

    @Test
    fun localFirstPolicyPrefersDeviceThenCheapestRemote() {
        val candidates =
            listOf(
                EngineDescriptor("remote-expensive", "p", remote, 3),
                EngineDescriptor("remote-cheap", "p", remote, 1),
                EngineDescriptor("device", "p", local, 0),
            )
        val ordered = LocalFirstPolicy.order(AiCapability.RecurringParsing, candidates, context())
        assertEquals(listOf("device", "remote-cheap", "remote-expensive"), ordered.map { it.id })
    }

    @Test
    fun routerFollowsPolicyOrder() =
        runTest {
            val expensive = recurringEngine("remote-expensive", remote, cost = 2)
            val cheap = recurringEngine("remote-cheap", remote, cost = 1)
            val device = recurringEngine("device", local)
            val result = router(expensive, cheap, device).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(charges("device"), assertIs<AiResult.Success<ParsedRecurringCharges>>(result).value)
            assertEquals("device", result.trace.servedBy?.id)
            assertTrue(expensive.inputs.isEmpty())
            assertTrue(cheap.inputs.isEmpty())
        }

    @Test
    fun customPolicyIsApplied() =
        runTest {
            val device = recurringEngine("device", local)
            val cloud = recurringEngine("cloud", remote)
            val remoteFirst = AiRoutingPolicy { _, candidates, _ -> candidates.sortedBy { it.location == local } }
            val routed = AiRouter(listOf(device, cloud), MutableContextSource(), remoteFirst)
            assertEquals(
                "cloud",
                routed
                    .run(AiCapability.RecurringParsing, sampleRequest)
                    .trace.servedBy
                    ?.id,
            )
        }

    @Test
    fun remotePersonalDataRequiresConsent() =
        runTest {
            val cloud = recurringEngine("cloud", remote)
            val source = MutableContextSource(context(consent = setOf(DataSensitivity.ANONYMIZED)))
            val result = router(cloud, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.ConsentRequired, assertIs<AiResult.Failure>(result).error)
            assertTrue(cloud.inputs.isEmpty())
            assertEquals(
                AttemptOutcome.Skipped(AiError.ConsentRequired),
                result.trace.attempts
                    .single()
                    .outcome,
            )
        }

    @Test
    fun onDeviceEngineNeedsNoConsentAndWorksOffline() =
        runTest {
            val device = recurringEngine("device", local)
            val source = MutableContextSource(context(online = false, credits = 0, consent = emptySet()))
            val result = router(device, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertIs<AiResult.Success<ParsedRecurringCharges>>(result)
        }

    @Test
    fun nonSensitiveCapabilityNeedsNoConsent() =
        runTest {
            val estimate = BudgetEstimate(0, 0, 0, emptyList(), emptyList(), "charges_fixes")
            val budget = FakeEngine(AiCapability.BudgetSuggestion, "budget", remote) { estimate }
            val source = MutableContextSource(context(consent = emptySet()))
            val result = router(budget, source = source).run(AiCapability.BudgetSuggestion, BudgetSuggestionRequest)
            assertEquals(estimate, assertIs<AiResult.Success<BudgetEstimate>>(result).value)
        }

    @Test
    fun remoteEngineIsSkippedOffline() =
        runTest {
            val cloud = recurringEngine("cloud", remote)
            val source = MutableContextSource(context(online = false))
            val result = router(cloud, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.Offline, assertIs<AiResult.Failure>(result).error)
            assertTrue(cloud.inputs.isEmpty())
        }

    @Test
    fun remoteEngineIsSkippedWithoutEnoughCredits() =
        runTest {
            val cloud = recurringEngine("cloud", remote, cost = 2)
            val source = MutableContextSource(context(credits = 1))
            val result = router(cloud, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.QuotaExceeded, assertIs<AiResult.Failure>(result).error)
            assertTrue(cloud.inputs.isEmpty())
        }

    @Test
    fun unknownCreditsDoNotBlock() =
        runTest {
            val cloud = recurringEngine("cloud", remote, cost = 5)
            val result =
                router(
                    cloud,
                    source = MutableContextSource(context(credits = null)),
                ).run(AiCapability.RecurringParsing, sampleRequest)
            assertIs<AiResult.Success<ParsedRecurringCharges>>(result)
        }

    @Test
    fun exactCreditsAreEnough() =
        runTest {
            val cloud = recurringEngine("cloud", remote, cost = 1)
            val result =
                router(
                    cloud,
                    source = MutableContextSource(context(credits = 1)),
                ).run(AiCapability.RecurringParsing, sampleRequest)
            assertIs<AiResult.Success<ParsedRecurringCharges>>(result)
        }

    @Test
    fun cheaperEngineIsUsedWhenCreditsOnlyCoverIt() =
        runTest {
            val cheap = recurringEngine("cheap", remote, cost = 0)
            val costly = recurringEngine("costly", remote, cost = 3)
            val source = MutableContextSource(context(credits = 0))
            val result = router(costly, cheap, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals("cheap", result.trace.servedBy?.id)
            assertTrue(costly.inputs.isEmpty())
        }

    @Test
    fun fallsBackToNextEngineAfterEngineFailure() =
        runTest {
            val device = recurringEngine("device", local, behaviour = failing(AiError.EngineFailure("model_missing")))
            val cloud = recurringEngine("cloud", remote)
            val result = router(device, cloud).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(charges("cloud"), assertIs<AiResult.Success<ParsedRecurringCharges>>(result).value)
            assertEquals(
                listOf(
                    AiAttempt(device.descriptor, AttemptOutcome.Failed(AiError.EngineFailure("model_missing"))),
                    AiAttempt(cloud.descriptor, AttemptOutcome.Succeeded),
                ),
                result.trace.attempts,
            )
        }

    @Test
    fun fallsBackAfterOfflineAndQuotaFailures() =
        runTest {
            val first = recurringEngine("first", remote, cost = 0, behaviour = failing(AiError.Offline))
            val second = recurringEngine("second", remote, cost = 1, behaviour = failing(AiError.QuotaExceeded))
            val third = recurringEngine("third", remote, cost = 2)
            val result = router(first, second, third).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals("third", result.trace.servedBy?.id)
        }

    @Test
    fun unexpectedExceptionBecomesEngineFailureWithoutMessage() =
        runTest {
            val device = recurringEngine("device", local) { throw IllegalStateException(SECRET_TEXT) }
            val cloud = recurringEngine("cloud", remote)
            val result = router(device, cloud).run(AiCapability.RecurringParsing, sampleRequest)
            val failed =
                result.trace.attempts
                    .first()
                    .outcome
            assertEquals(AttemptOutcome.Failed(AiError.EngineFailure("IllegalStateException")), failed)
            assertEquals("cloud", result.trace.servedBy?.id)
        }

    @Test
    fun rejectionStopsTheChain() =
        runTest {
            val device = recurringEngine("device", local, behaviour = failing(AiError.Rejected("NO_CATEGORY")))
            val cloud = recurringEngine("cloud", remote)
            val result = router(device, cloud).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.Rejected("NO_CATEGORY"), assertIs<AiResult.Failure>(result).error)
            assertTrue(cloud.inputs.isEmpty())
        }

    @Test
    fun unauthorizedStopsTheChain() =
        runTest {
            val first = recurringEngine("first", remote, cost = 0, behaviour = failing(AiError.Unauthorized))
            val second = recurringEngine("second", remote, cost = 1)
            val result = router(first, second).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.Unauthorized, assertIs<AiResult.Failure>(result).error)
            assertTrue(second.inputs.isEmpty())
        }

    @Test
    fun consentRequiredThrownByEngineStopsTheChain() =
        runTest {
            val first = recurringEngine("first", local, behaviour = failing(AiError.ConsentRequired))
            val second = recurringEngine("second", remote)
            val result = router(first, second).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.ConsentRequired, assertIs<AiResult.Failure>(result).error)
            assertTrue(second.inputs.isEmpty())
        }

    @Test
    fun unavailableEngineIsSkipped() =
        runTest {
            val device =
                recurringEngine("device", local, availability = EngineAvailability.Unavailable("model_not_downloaded"))
            val cloud = recurringEngine("cloud", remote)
            val result = router(device, cloud).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals("cloud", result.trace.servedBy?.id)
            assertTrue(device.inputs.isEmpty())
            assertEquals(
                AttemptOutcome.Skipped(AiError.EngineFailure("model_not_downloaded")),
                result.trace.attempts
                    .first()
                    .outcome,
            )
        }

    @Test
    fun onlyUnavailableEnginesYieldEngineFailure() =
        runTest {
            val device = recurringEngine("device", local, availability = EngineAvailability.Unavailable("no_model"))
            val result = router(device).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.EngineFailure("no_model"), assertIs<AiResult.Failure>(result).error)
        }

    @Test
    fun noEngineForCapability() =
        runTest {
            val source = MutableContextSource()
            val listener = RecordingListener()
            val result =
                router(
                    recurringEngine("device", local),
                    source = source,
                    listener = listener,
                ).run(AiCapability.SpeechToText, speech)
            assertEquals(AiError.NoEngine, assertIs<AiResult.Failure>(result).error)
            assertTrue(result.trace.attempts.isEmpty())
            assertEquals(0, source.calls)
            assertEquals(listOf(result.trace), listener.traces)
        }

    @Test
    fun traceIsReportedOnceForEveryRun() =
        runTest {
            val listener = RecordingListener()
            val routed = router(recurringEngine("device", local), listener = listener)
            routed.run(AiCapability.RecurringParsing, sampleRequest)
            routed.run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(2, listener.traces.size)
            assertTrue(listener.traces.all { it.capability == "recurring_parsing" })
        }

    @Test
    fun traceNeverContainsUserData() =
        runTest {
            val listener = RecordingListener()
            val device = recurringEngine("device", local) { throw IllegalStateException(SECRET_TEXT) }
            val cloud = recurringEngine("cloud", remote, behaviour = failing(AiError.EngineFailure("timeout")))
            val result = router(device, cloud, listener = listener).run(AiCapability.RecurringParsing, sampleRequest)
            val rendered = listener.traces.single().toString() + result.toString()
            assertFalse(rendered.contains("Dupont"))
            assertFalse(rendered.contains("FR76"))
            assertNull(result.trace.servedBy)
        }

    @Test
    fun sensitiveTypesHideTheirContent() {
        assertFalse(RawStatement(SECRET_TEXT, listOf("Dupont")).toString().contains("Dupont"))
        assertFalse(AudioClip(SECRET_TEXT.encodeToByteArray(), AudioEncoding.WAV_PCM16, 16_000, 1_000).toString().contains("Dupont"))
    }

    @Test
    fun cancellationIsPropagated() =
        runTest {
            val device = recurringEngine("device", local) { throw CancellationException("stop") }
            val cloud = recurringEngine("cloud", remote)
            val listener = RecordingListener()
            assertFailsWith<CancellationException> {
                router(device, cloud, listener = listener).run(AiCapability.RecurringParsing, sampleRequest)
            }
            assertTrue(cloud.inputs.isEmpty())
            assertTrue(listener.traces.isEmpty())
        }

    @Test
    fun consentRequiredOutranksOtherErrors() =
        runTest {
            val device = recurringEngine("device", local, behaviour = failing(AiError.EngineFailure("x")))
            val cloud = recurringEngine("cloud", remote)
            val source = MutableContextSource(context(online = false, consent = emptySet()))
            val result = router(device, cloud, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.ConsentRequired, assertIs<AiResult.Failure>(result).error)
        }

    @Test
    fun quotaOutranksOfflineAndEngineFailure() =
        runTest {
            val device = recurringEngine("device", local, behaviour = failing(AiError.EngineFailure("x")))
            val costly = recurringEngine("costly", remote, cost = 5)
            val failingRemote = recurringEngine("remote", remote, cost = 0, behaviour = failing(AiError.Offline))
            val source = MutableContextSource(context(credits = 1))
            val result = router(device, costly, failingRemote, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.QuotaExceeded, assertIs<AiResult.Failure>(result).error)
        }

    @Test
    fun offlineOutranksEngineFailure() =
        runTest {
            val device = recurringEngine("device", local, behaviour = failing(AiError.EngineFailure("x")))
            val cloud = recurringEngine("cloud", remote)
            val source = MutableContextSource(context(online = false))
            val result = router(device, cloud, source = source).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.Offline, assertIs<AiResult.Failure>(result).error)
        }

    @Test
    fun lastEngineFailureIsReportedWhenNothingElseApplies() =
        runTest {
            val first = recurringEngine("first", local, behaviour = failing(AiError.EngineFailure("first")))
            val second = recurringEngine("second", remote, behaviour = failing(AiError.EngineFailure("second")))
            val result = router(first, second).run(AiCapability.RecurringParsing, sampleRequest)
            assertEquals(AiError.EngineFailure("second"), assertIs<AiResult.Failure>(result).error)
        }

    @Test
    fun allowsFallbackOnlyForTransientErrors() {
        assertTrue(AiError.EngineFailure("x").allowsFallback)
        assertTrue(AiError.Offline.allowsFallback)
        assertTrue(AiError.QuotaExceeded.allowsFallback)
        listOf(
            AiError.NoEngine,
            AiError.ConsentRequired,
            AiError.Unauthorized,
            AiError.Rejected(null),
            AiError.PrivacyViolation,
        ).forEach { assertFalse(it.allowsFallback, it.toString()) }
    }

    private val speech = SpeechRequest(AudioClip(ByteArray(4), AudioEncoding.WAV_PCM16, 16_000, 10), "fr")
}

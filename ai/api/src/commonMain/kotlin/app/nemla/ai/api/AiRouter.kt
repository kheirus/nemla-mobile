package app.nemla.ai.api

import kotlin.coroutines.cancellation.CancellationException

/** Facts about "now" that decide whether data may leave the device. Supplied by the app. */
data class AiRoutingContext(
    val online: Boolean,
    /** Credits left this month, or null when unknown (the backend stays the authority). */
    val remainingCredits: Int?,
    /** Sensitivity levels the user agreed to send to remote engines. */
    val remoteConsent: Set<DataSensitivity>,
)

fun interface AiContextSource {
    suspend fun current(): AiRoutingContext
}

/** Orders the engines that passed the hard rules. Swap it to change strategy (cost, quality, A/B). */
fun interface AiRoutingPolicy {
    fun order(
        capability: AiCapability<*, *>,
        candidates: List<EngineDescriptor>,
        context: AiRoutingContext,
    ): List<EngineDescriptor>
}

/** Default strategy: on the device whenever possible, then the cheapest remote engine. */
object LocalFirstPolicy : AiRoutingPolicy {
    override fun order(
        capability: AiCapability<*, *>,
        candidates: List<EngineDescriptor>,
        context: AiRoutingContext,
    ): List<EngineDescriptor> =
        candidates.sortedWith(compareBy({ it.location != ExecutionLocation.ON_DEVICE }, { it.creditCost }))
}

sealed interface AttemptOutcome {
    data object Succeeded : AttemptOutcome
    data class Skipped(val error: AiError) : AttemptOutcome
    data class Failed(val error: AiError) : AttemptOutcome
}

data class AiAttempt(val engine: EngineDescriptor, val outcome: AttemptOutcome)

/** What happened for one request: which engines were considered and why. Contains no user data. */
data class AiTrace(val capability: String, val attempts: List<AiAttempt>) {
    val servedBy: EngineDescriptor?
        get() = attempts.firstOrNull { it.outcome == AttemptOutcome.Succeeded }?.engine
}

sealed interface AiResult<out T> {
    val trace: AiTrace

    data class Success<T>(val value: T, override val trace: AiTrace) : AiResult<T>
    data class Failure(val error: AiError, override val trace: AiTrace) : AiResult<Nothing>
}

/** Receives traces for diagnostics. Implementations must not log payloads. */
fun interface AiTraceListener {
    fun onTrace(trace: AiTrace)
}

/**
 * Single entry point of the app into AI.
 *
 * Callers ask for a capability; the router picks engines, enforces the privacy rules and falls
 * back from one engine to the next. Rules that are not negotiable live here, not in the policy:
 *
 * 1. A capability whose input is [DataSensitivity.RAW_FINANCIAL] only ever runs on the device.
 * 2. A remote engine runs only if the user consented to sending that sensitivity level.
 * 3. A remote engine is skipped when offline or when its cost exceeds the known credits.
 */
class AiRouter(
    engines: List<AiEngine<*, *>>,
    private val contextSource: AiContextSource,
    private val policy: AiRoutingPolicy = LocalFirstPolicy,
    private val listener: AiTraceListener? = null,
) {
    private val engines: List<AiEngine<*, *>> = engines.toList()

    init {
        val leaking = this.engines.filter {
            it.capability.inputSensitivity == DataSensitivity.RAW_FINANCIAL &&
                it.descriptor.location == ExecutionLocation.REMOTE
        }
        require(leaking.isEmpty()) {
            "Remote engines cannot serve raw financial data: ${leaking.map { it.descriptor.id }}"
        }
        val ids = this.engines.map { it.descriptor.id }
        require(ids.size == ids.toSet().size) { "Engine ids must be unique: $ids" }
    }

    /** Engines registered for a capability, in registration order. */
    fun enginesFor(capability: AiCapability<*, *>): List<EngineDescriptor> =
        engines.filter { it.capability == capability }.map { it.descriptor }

    suspend fun <I : Any, O : Any> run(capability: AiCapability<I, O>, input: I): AiResult<O> {
        // Sound: an engine's capability object fixes its input and output types.
        @Suppress("UNCHECKED_CAST")
        val registered = engines.filter { it.capability == capability } as List<AiEngine<I, O>>
        val attempts = mutableListOf<AiAttempt>()

        fun finish(result: (AiTrace) -> AiResult<O>): AiResult<O> {
            val trace = AiTrace(capability.key, attempts.toList())
            listener?.onTrace(trace)
            return result(trace)
        }

        if (registered.isEmpty()) return finish { AiResult.Failure(AiError.NoEngine, it) }

        val context = contextSource.current()
        val eligible = registered.filter { engine ->
            val blocker = hardRuleBlocker(capability, engine.descriptor, context)
            if (blocker != null) attempts += AiAttempt(engine.descriptor, AttemptOutcome.Skipped(blocker))
            blocker == null
        }
        val byId = eligible.associateBy { it.descriptor.id }
        val ordered = policy.order(capability, eligible.map { it.descriptor }, context)
            .mapNotNull { byId[it.id] }

        for (engine in ordered) {
            val availability = engine.availability()
            if (availability is EngineAvailability.Unavailable) {
                val error = AiError.EngineFailure(availability.reason)
                attempts += AiAttempt(engine.descriptor, AttemptOutcome.Skipped(error))
                continue
            }
            val error = try {
                val value = engine.execute(input)
                attempts += AiAttempt(engine.descriptor, AttemptOutcome.Succeeded)
                return finish { AiResult.Success(value, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                e.error
            } catch (e: Exception) {
                // Message deliberately dropped: provider errors may echo user content.
                AiError.EngineFailure(e::class.simpleName ?: "error")
            }
            attempts += AiAttempt(engine.descriptor, AttemptOutcome.Failed(error))
            if (!error.allowsFallback) return finish { AiResult.Failure(error, it) }
        }

        return finish { AiResult.Failure(mostMeaningfulError(attempts), it) }
    }

    private fun hardRuleBlocker(
        capability: AiCapability<*, *>,
        engine: EngineDescriptor,
        context: AiRoutingContext,
    ): AiError? {
        if (engine.location == ExecutionLocation.ON_DEVICE) return null
        val sensitivity = capability.inputSensitivity
        return when {
            sensitivity == DataSensitivity.RAW_FINANCIAL -> AiError.PrivacyViolation
            sensitivity != DataSensitivity.NONE && sensitivity !in context.remoteConsent -> AiError.ConsentRequired
            !context.online -> AiError.Offline
            context.remainingCredits != null && engine.creditCost > context.remainingCredits -> AiError.QuotaExceeded
            else -> null
        }
    }

    /** When nothing ran to completion, report the cause the user can act on first. */
    private fun mostMeaningfulError(attempts: List<AiAttempt>): AiError {
        val errors = attempts.mapNotNull {
            when (val outcome = it.outcome) {
                is AttemptOutcome.Failed -> outcome.error
                is AttemptOutcome.Skipped -> outcome.error
                AttemptOutcome.Succeeded -> null
            }
        }
        val priority = listOf(
            AiError.ConsentRequired,
            AiError.QuotaExceeded,
            AiError.Offline,
        )
        return priority.firstOrNull { it in errors }
            ?: errors.lastOrNull { it is AiError.EngineFailure }
            ?: errors.lastOrNull()
            ?: AiError.NoEngine
    }
}

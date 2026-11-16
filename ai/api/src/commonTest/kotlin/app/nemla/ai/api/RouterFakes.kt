package app.nemla.ai.api

internal const val SECRET_TEXT = "Jean Dupont FR7630006000011234567890189"

internal fun context(
    online: Boolean = true,
    credits: Int? = null,
    consent: Set<DataSensitivity> = setOf(DataSensitivity.PERSONAL, DataSensitivity.ANONYMIZED),
): AiRoutingContext = AiRoutingContext(online = online, remainingCredits = credits, remoteConsent = consent)

internal class MutableContextSource(
    var value: AiRoutingContext = context(),
) : AiContextSource {
    var calls = 0

    override suspend fun current(): AiRoutingContext {
        calls++
        return value
    }
}

internal class RecordingListener : AiTraceListener {
    val traces = mutableListOf<AiTrace>()

    override fun onTrace(trace: AiTrace) {
        traces += trace
    }
}

internal class FakeEngine<I : Any, O : Any>(
    override val capability: AiCapability<I, O>,
    id: String,
    location: ExecutionLocation,
    cost: Int = 0,
    private val availability: EngineAvailability = EngineAvailability.Ready,
    private val behaviour: suspend (I) -> O,
) : AiEngine<I, O> {
    override val descriptor: EngineDescriptor = EngineDescriptor(id, "fake", location, cost)

    val inputs = mutableListOf<I>()

    override suspend fun availability(): EngineAvailability = availability

    override suspend fun execute(input: I): O {
        inputs += input
        return behaviour(input)
    }
}

internal val sampleRequest = RecurringParsingRequest(SECRET_TEXT, "fr")

internal fun charges(name: String): ParsedRecurringCharges =
    ParsedRecurringCharges(listOf(RecurringChargeDraft(name, 1_000, ChargeFrequency.MONTHLY, 5, null)))

internal fun recurringEngine(
    id: String,
    location: ExecutionLocation,
    cost: Int = 0,
    availability: EngineAvailability = EngineAvailability.Ready,
    behaviour: suspend (RecurringParsingRequest) -> ParsedRecurringCharges = { charges(id) },
): FakeEngine<RecurringParsingRequest, ParsedRecurringCharges> =
    FakeEngine(AiCapability.RecurringParsing, id, location, cost, availability, behaviour)

internal fun failing(error: AiError): suspend (RecurringParsingRequest) -> ParsedRecurringCharges = { throw AiException(error) }

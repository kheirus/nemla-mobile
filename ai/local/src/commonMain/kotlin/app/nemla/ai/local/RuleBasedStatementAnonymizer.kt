package app.nemla.ai.local

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AnonymizedText
import app.nemla.ai.api.EngineDescriptor
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.PrivacyBoundary
import app.nemla.ai.api.RawStatement

class RuleBasedStatementAnonymizer : AiEngine<RawStatement, AnonymizedText> {
    override val capability: AiCapability<RawStatement, AnonymizedText> = AiCapability.StatementAnonymization

    override val descriptor: EngineDescriptor =
        EngineDescriptor(
            id = ENGINE_ID,
            provider = PROVIDER,
            location = ExecutionLocation.ON_DEVICE,
            creditCost = 0,
        )

    @OptIn(PrivacyBoundary::class)
    override suspend fun execute(input: RawStatement): AnonymizedText {
        val result = StatementAnonymizer.anonymize(input.text, input.knownNames)
        return AnonymizedText(result.text, result.report)
    }

    override fun toString(): String = "RuleBasedStatementAnonymizer"

    companion object {
        const val ENGINE_ID: String = "local-rule-anonymizer"
        const val PROVIDER: String = "nemla-local"
    }
}

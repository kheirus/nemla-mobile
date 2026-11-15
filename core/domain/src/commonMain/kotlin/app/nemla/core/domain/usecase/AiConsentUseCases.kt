package app.nemla.core.domain.usecase

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.port.AiConsentRepository
import kotlinx.coroutines.flow.Flow

class ObserveAiConsents(
    private val consents: AiConsentRepository,
) {
    operator fun invoke(): Flow<AiConsents> = consents.observe()
}

class GrantAiConsent(
    private val consents: AiConsentRepository,
) {
    suspend operator fun invoke(level: AiConsentLevel): AppResult<Unit> = consents.grant(level)
}

class RevokeAiConsent(
    private val consents: AiConsentRepository,
) {
    suspend operator fun invoke(level: AiConsentLevel): AppResult<Unit> = consents.revoke(level)
}

class RevokeAllAiConsents(
    private val consents: AiConsentRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        for (level in AiConsentLevel.entries) {
            val result = consents.revoke(level)
            if (result is AppResult.Failure) return result
        }
        return AppResult.Success(Unit)
    }
}

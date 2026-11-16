package app.nemla.core.data.ai

import app.nemla.ai.api.AiContextSource
import app.nemla.ai.api.AiRoutingContext
import app.nemla.ai.api.DataSensitivity
import app.nemla.core.common.getOrNull
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.QuotaRepository

interface ConnectivityMonitor {
    suspend fun isOnline(): Boolean
}

internal object AssumeOnlineConnectivityMonitor : ConnectivityMonitor {
    override suspend fun isOnline(): Boolean = true
}

internal class RepositoryAiContextSource(
    private val connectivity: ConnectivityMonitor,
    private val quotas: QuotaRepository,
    private val consents: AiConsentRepository,
) : AiContextSource {
    override suspend fun current(): AiRoutingContext {
        val online = connectivity.isOnline()
        val credits = if (online) quotas.currentMonth().getOrNull()?.voiceRemaining else null
        val granted = consents.current().getOrNull() ?: AiConsents.None
        return AiRoutingContext(online = online, remainingCredits = credits, remoteConsent = granted.toSensitivities())
    }
}

internal fun AiConsents.toSensitivities(): Set<DataSensitivity> =
    buildSet {
        if (isGranted(AiConsentLevel.ANONYMIZED)) add(DataSensitivity.ANONYMIZED)
        if (isGranted(AiConsentLevel.PERSONAL)) {
            add(DataSensitivity.PERSONAL)
            add(DataSensitivity.ANONYMIZED)
        }
    }

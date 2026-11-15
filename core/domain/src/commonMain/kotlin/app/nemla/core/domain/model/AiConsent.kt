package app.nemla.core.domain.model

import kotlin.time.Instant

enum class AiConsentLevel { ANONYMIZED, PERSONAL }

data class AiConsents(
    val grants: Map<AiConsentLevel, Instant>,
) {
    val levels: Set<AiConsentLevel> get() = grants.keys

    fun isGranted(level: AiConsentLevel): Boolean = level in grants

    companion object {
        val None: AiConsents = AiConsents(emptyMap())
    }
}

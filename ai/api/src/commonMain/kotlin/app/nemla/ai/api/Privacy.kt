package app.nemla.ai.api

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Only an anonymiser may assert that data is anonymised. Do not opt in elsewhere.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class PrivacyBoundary

enum class DataSensitivity {
    NONE,

    ANONYMIZED,

    PERSONAL,

    RAW_FINANCIAL,
}

enum class PiiKind {
    IBAN,
    BIC,
    CARD_NUMBER,
    ACCOUNT_NUMBER,
    PERSON_NAME,
    POSTAL_ADDRESS,
    EMAIL,
    PHONE,
    NATIONAL_ID,
    OTHER,
}

data class RedactionReport(
    val counts: Map<PiiKind, Int>,
) {
    val total: Int get() = counts.values.sum()

    companion object {
        val Empty = RedactionReport(emptyMap())
    }
}

class AnonymizedText
    @PrivacyBoundary
    constructor(
        val value: String,
        val report: RedactionReport,
    ) {
        override fun toString(): String = "AnonymizedText(length=${value.length}, redactions=${report.total})"
    }

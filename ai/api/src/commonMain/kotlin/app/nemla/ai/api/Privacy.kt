package app.nemla.ai.api

/**
 * Marks the only places allowed to assert that a piece of data has been anonymised.
 *
 * Constructing an [AnonymizedText] requires opting in to this annotation, so a grep for
 * `@OptIn(PrivacyBoundary::class)` lists every place where the privacy guarantee is produced.
 * Only anonymiser implementations (module `:ai:local`) and their tests may opt in.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Only an anonymiser may assert that data is anonymised. Do not opt in elsewhere.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class PrivacyBoundary

/** How sensitive a payload is. Ordered from least to most sensitive. */
enum class DataSensitivity {
    /** No payload leaves the device (the engine works from data it already holds). */
    NONE,

    /** Went through an anonymiser: no direct identifier left. */
    ANONYMIZED,

    /** Personal but user-authored and short-lived (voice note, dictated sentence). */
    PERSONAL,

    /** Raw banking document: identifiers, account numbers, names, addresses. Never leaves the device. */
    RAW_FINANCIAL,
}

/** Kinds of personal identifiers an anonymiser removes. */
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

/** What an anonymiser removed. Holds counts only, never the removed values. */
data class RedactionReport(val counts: Map<PiiKind, Int>) {
    val total: Int get() = counts.values.sum()

    companion object {
        val Empty = RedactionReport(emptyMap())
    }
}

/**
 * Text that is proven, by construction, to have gone through an anonymiser.
 *
 * Remote engines that handle banking documents take this type and nothing else, which makes
 * "raw statement sent to the network" a compile error rather than a code-review item.
 */
class AnonymizedText @PrivacyBoundary constructor(
    val value: String,
    val report: RedactionReport,
) {
    // Never print the content: logs must stay free of financial data.
    override fun toString(): String = "AnonymizedText(length=${value.length}, redactions=${report.total})"
}

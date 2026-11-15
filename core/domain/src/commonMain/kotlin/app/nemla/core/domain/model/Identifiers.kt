package app.nemla.core.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class UserId(
    val value: String,
)

@JvmInline
value class CategoryId(
    val value: String,
)

@JvmInline
value class ExpenseId(
    val value: String,
)

@JvmInline
value class VoiceEntryId(
    val value: String,
)

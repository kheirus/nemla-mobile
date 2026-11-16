package app.nemla.core.data.ai

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.StatementFile
import app.nemla.core.domain.model.StatementFormat

internal interface StatementTextExtractor {
    suspend fun extract(file: StatementFile): AppResult<String>
}

internal object PlainTextStatementExtractor : StatementTextExtractor {
    const val MAX_BYTES: Int = 8 * 1024 * 1024
    private const val BYTE_ORDER_MARK = '﻿'

    override suspend fun extract(file: StatementFile): AppResult<String> =
        when {
            file.format == StatementFormat.PDF || file.format == StatementFormat.SPREADSHEET -> {
                AppResult.Failure(AppError.NotSupportedYet)
            }

            file.bytes.size > MAX_BYTES -> {
                AppResult.Failure(AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE))
            }

            else -> {
                AppResult.Success(file.bytes.decodeToString().removePrefix(BYTE_ORDER_MARK.toString()))
            }
        }
}

package app.nemla.core.data.account

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AccountExport
import app.nemla.core.domain.port.AccountRepository

internal object UnsupportedAccountRepository : AccountRepository {
    override suspend fun deleteAccount(): AppResult<Unit> = AppResult.Failure(AppError.NotSupportedYet)

    override suspend fun exportData(): AppResult<AccountExport> = AppResult.Failure(AppError.NotSupportedYet)
}

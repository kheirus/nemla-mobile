package app.nemla.core.domain.port

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ScheduledNotice

interface LocalNotifier {
    suspend fun access(): NotificationAccess

    suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit>

    suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit>

    suspend fun cancelAll(): AppResult<Unit>
}

interface NotificationMemory {
    suspend fun wasAlerted(key: String): AppResult<Boolean>

    suspend fun remember(key: String): AppResult<Unit>

    suspend fun clear(): AppResult<Unit>
}

interface InboxRepository {
    suspend fun unseen(): AppResult<List<InboxItem>>

    suspend fun markSeen(id: InboxItemId): AppResult<Unit>

    suspend fun markDelivered(id: InboxItemId): AppResult<Unit>
}

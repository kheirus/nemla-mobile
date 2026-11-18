package app.nemla.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.common.runCatchingApp
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ScheduledNotice
import app.nemla.core.domain.port.LocalNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal class AndroidLocalNotifier(
    context: Context,
    private val io: CoroutineDispatcher,
) : LocalNotifier {
    private val context = context.applicationContext
    private val plans = NotificationPlanStore(this.context)
    private val alarms = NoticeAlarms(this.context)
    private val mutex = Mutex()

    override suspend fun access(): NotificationAccess = readAccess(context)

    override suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit> =
        withContext(io) {
            mutex.withLock {
                runCatchingApp {
                    plans.read().filter { it.group == group.name }.forEach(alarms::cancel)
                    val zone = TimeZone.currentSystemDefault()
                    val planned =
                        notices.take(GROUP_STRIDE).mapIndexed { index, scheduled ->
                            val text = NotificationCopy.text(scheduled.notice, language)
                            PlannedNotice(
                                requestCode = group.ordinal * GROUP_STRIDE + index,
                                group = group.name,
                                channel = NoticeChannel.of(scheduled.notice),
                                language = language,
                                title = text.title,
                                body = text.body,
                                atMillis = scheduled.at.toInstant(zone).toEpochMilliseconds(),
                            )
                        }
                    plans.replaceGroup(group.name, planned)
                    planned.forEach(alarms::set)
                }
            }
        }

    override suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit> =
        withContext(io) {
            val posted =
                runCatchingApp {
                    NoticePoster.post(
                        context = context,
                        tag = tag,
                        id = SHOWN_ID,
                        channel = NoticeChannel.of(notice),
                        language = language,
                        text = NotificationCopy.text(notice, language),
                        isPrivate = NotificationCopy.isPrivate(notice),
                    )
                }
            if (posted.getOrNull() == true) AppResult.Success(Unit) else AppResult.Failure(AppError.Unknown)
        }

    override suspend fun cancelAll(): AppResult<Unit> =
        withContext(io) {
            mutex.withLock {
                runCatchingApp {
                    plans.read().forEach(alarms::cancel)
                    plans.clear()
                    NotificationManagerCompat.from(context).cancelAll()
                }
            }
        }

    private companion object {
        const val GROUP_STRIDE = 1_000
        const val SHOWN_ID = 1
    }
}

internal fun readAccess(context: Context): NotificationAccess =
    when {
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
            NotificationAccess.GRANTED
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
            NotificationAccess.UNDETERMINED
        }

        else -> {
            NotificationAccess.DENIED
        }
    }

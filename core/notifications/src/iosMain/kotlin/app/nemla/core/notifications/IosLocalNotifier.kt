package app.nemla.core.notifications

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ScheduledNotice
import app.nemla.core.domain.port.LocalNotifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal class IosLocalNotifier : LocalNotifier {
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun access(): NotificationAccess =
        suspendCancellableCoroutine { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
                val access =
                    when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> {
                            NotificationAccess.GRANTED
                        }

                        UNAuthorizationStatusDenied -> {
                            NotificationAccess.DENIED
                        }

                        else -> {
                            NotificationAccess.UNDETERMINED
                        }
                    }
                continuation.resume(access)
            }
        }

    override suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit> {
        val prefix = group.name + "-"
        val stale = pendingIdentifiers().filter { it.startsWith(prefix) }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
        var failed = false
        notices.forEachIndexed { index, scheduled ->
            val components =
                NSDateComponents().apply {
                    year = scheduled.at.year.toLong()
                    month =
                        scheduled.at.month.number
                            .toLong()
                    day = scheduled.at.day.toLong()
                    hour = scheduled.at.hour.toLong()
                    minute = scheduled.at.minute.toLong()
                }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            if (!add("$prefix$index", NotificationCopy.text(scheduled.notice, language), trigger)) failed = true
        }
        return if (failed) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
    }

    override suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit> =
        if (add(SHOWN_PREFIX + tag, NotificationCopy.text(notice, language), null)) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(AppError.Unknown)
        }

    override suspend fun cancelAll(): AppResult<Unit> {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
        return AppResult.Success(Unit)
    }

    private suspend fun pendingIdentifiers(): List<String> =
        suspendCancellableCoroutine { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                continuation.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier })
            }
        }

    private suspend fun add(
        identifier: String,
        text: NoticeText,
        trigger: UNNotificationTrigger?,
    ): Boolean {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(text.title)
                setBody(text.body)
                setSound(UNNotificationSound.defaultSound())
            }
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        return suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { error -> continuation.resume(error == null) }
        }
    }

    private companion object {
        const val SHOWN_PREFIX = "shown-"
    }
}

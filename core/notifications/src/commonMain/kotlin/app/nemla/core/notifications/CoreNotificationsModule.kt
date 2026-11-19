package app.nemla.core.notifications

import app.nemla.core.domain.usecase.AlertReachedBudgets
import app.nemla.core.domain.usecase.ClearNotifications
import app.nemla.core.domain.usecase.DeliverPushInbox
import app.nemla.core.domain.usecase.DismissInboxItem
import app.nemla.core.domain.usecase.GetNotificationAccess
import app.nemla.core.domain.usecase.ListInAppNotices
import app.nemla.core.domain.usecase.RefreshScheduledNotifications
import app.nemla.core.domain.usecase.SetDailyReminder
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

private val notificationUseCasesModule: Module =
    module {
        factory { RefreshScheduledNotifications(notifier = get(), profiles = get(), expenses = get(), languages = get()) }
        factoryOf(::GetNotificationAccess)
        factoryOf(::SetDailyReminder)
        factoryOf(::AlertReachedBudgets)
        factoryOf(::DeliverPushInbox)
        factoryOf(::ListInAppNotices)
        factoryOf(::DismissInboxItem)
        factoryOf(::ClearNotifications)
    }

val coreNotificationsModule: Module =
    module {
        includes(platformNotificationsModule, notificationUseCasesModule)
    }

internal expect val platformNotificationsModule: Module

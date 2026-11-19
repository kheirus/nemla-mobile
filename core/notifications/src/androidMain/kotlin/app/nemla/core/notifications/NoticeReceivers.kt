package app.nemla.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class NoticeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != NoticeAlarms.ACTION_NOTICE) return
        val requestCode = intent.getIntExtra(NoticeAlarms.EXTRA_REQUEST_CODE, NO_CODE)
        if (requestCode == NO_CODE) return
        val planned = NotificationPlanStore(context).find(requestCode) ?: return
        NoticePoster.post(
            context = context,
            tag = planned.group,
            id = planned.requestCode,
            channel = planned.channel,
            language = planned.language,
            text = planned.text,
            isPrivate = false,
        )
    }

    private companion object {
        const val NO_CODE = -1
    }
}

internal class NoticeRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val alarms = NoticeAlarms(context)
        NotificationPlanStore(context).keepFuture(System.currentTimeMillis()).forEach(alarms::set)
    }
}

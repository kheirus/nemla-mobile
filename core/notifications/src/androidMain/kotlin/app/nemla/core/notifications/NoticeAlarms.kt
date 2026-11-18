package app.nemla.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal class NoticeAlarms(
    private val context: Context,
) {
    private val manager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun set(planned: PlannedNotice) {
        val pending = PendingIntent.getBroadcast(context, planned.requestCode, intent(planned.requestCode), FLAGS) ?: return
        manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, planned.atMillis, pending)
    }

    fun cancel(planned: PlannedNotice) {
        val pending =
            PendingIntent.getBroadcast(
                context,
                planned.requestCode,
                intent(planned.requestCode),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: return
        manager?.cancel(pending)
        pending.cancel()
    }

    private fun intent(requestCode: Int): Intent =
        Intent(context, NoticeAlarmReceiver::class.java)
            .setAction(ACTION_NOTICE)
            .putExtra(EXTRA_REQUEST_CODE, requestCode)

    companion object {
        const val ACTION_NOTICE: String = "app.nemla.core.notifications.NOTICE"
        const val EXTRA_REQUEST_CODE: String = "requestCode"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

package app.nemla.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nemla.core.domain.model.AppLanguage

internal object NoticePoster {
    fun post(
        context: Context,
        tag: String,
        id: Int,
        channel: NoticeChannel,
        language: AppLanguage,
        text: NoticeText,
        isPrivate: Boolean,
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || !hasPermission(context)) return false
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channel.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(NotificationCopy.channelName(channel, language))
                .build(),
        )
        val builder =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(R.drawable.nemla_notification_icon)
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
        launchIntent(context)?.let(builder::setContentIntent)
        if (isPrivate) {
            builder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat
                        .Builder(context, channel.id)
                        .setSmallIcon(R.drawable.nemla_notification_icon)
                        .setContentTitle(NotificationCopy.BRAND)
                        .build(),
                )
        }
        return try {
            manager.notify(tag, id, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun launchIntent(context: Context): PendingIntent? {
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            } ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

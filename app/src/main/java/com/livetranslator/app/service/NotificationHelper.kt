package com.livetranslator.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.livetranslator.app.MainActivity
import com.livetranslator.app.R

object NotificationHelper {

  const val CHANNEL_ID = "live_translator_channel"
  const val ALERTS_CHANNEL_ID = "live_translator_alerts_channel"
  const val TRANSLATION_NOTIFICATION_ID = 1001
  const val OVERLAY_NOTIFICATION_ID = 1002
  const val UPDATE_ALERT_NOTIFICATION_ID = 2001

  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
          }
      manager.createNotificationChannel(channel)
    }

    if (manager.getNotificationChannel(ALERTS_CHANNEL_ID) == null) {
      val alertsChannel =
        NotificationChannel(
            ALERTS_CHANNEL_ID,
            context.getString(R.string.notification_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = context.getString(R.string.notification_alerts_channel_desc)
            setShowBadge(true)
          }
      manager.createNotificationChannel(alertsChannel)
    }
  }

  fun showAlertNotification(
    context: Context,
    title: String,
    body: String,
    notificationId: Int = (System.currentTimeMillis() % 100_000).toInt() + 3000,
    openUpdatesScreen: Boolean = false,
  ): Boolean {
    ensureChannel(context)
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return false
    }

    val openIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
          if (openUpdatesScreen) {
            putExtra(MainActivity.EXTRA_OPEN_UPDATES_CENTER, true)
          }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bell)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(openIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    manager.notify(notificationId, notification)
    return true
  }

  fun buildTranslationNotification(
    context: Context,
    statusSubtitle: String? = null,
  ): Notification {
    ensureChannel(context)
    val openIntent =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      PendingIntent.getService(
        context,
        1,
        Intent(context, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_STOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_translate)
      .setContentTitle(context.getString(R.string.notification_title_active))
      .setContentText(statusSubtitle ?: context.getString(R.string.notification_text_listening))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openIntent)
      .addAction(
        R.drawable.ic_stop,
        context.getString(R.string.notification_action_stop),
        stopIntent,
      )
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  fun buildOverlayStandbyNotification(context: Context): Notification {
    ensureChannel(context)
    val openIntent =
      PendingIntent.getActivity(
        context,
        2,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopOverlayIntent =
      PendingIntent.getService(
        context,
        3,
        Intent(context, OverlayService::class.java).apply {
          action = OverlayService.ACTION_STOP_OVERLAY
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_bubble)
      .setContentTitle(context.getString(R.string.notification_title_standby))
      .setContentText(context.getString(R.string.notification_text_standby))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openIntent)
      .addAction(
        R.drawable.ic_stop,
        context.getString(R.string.notification_action_stop),
        stopOverlayIntent,
      )
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }
}

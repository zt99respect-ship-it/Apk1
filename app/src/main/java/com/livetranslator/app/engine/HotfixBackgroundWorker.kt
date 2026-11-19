package com.livetranslator.app.engine

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.livetranslator.app.LiveTranslatorApp
import java.util.concurrent.TimeUnit

/**
 * Background incremental hotfix profile worker.
 * Periodically inspects `/hotfix_profiles/live` and any configured OTA JSON feed
 * and notifies the user inside the app & system notification shade when a patch is ready
 * without forcing a full APK reinstall.
 */
class HotfixBackgroundWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val app = applicationContext as? LiveTranslatorApp ?: return Result.success()
    return runCatching {
        app.otaUpdateManager.initializeFromDisk()

        // 1. Check cloud Hotfix Profile if signed in
        val user = Firebase.auth.currentUser
        if (user != null) {
          app.cloudSyncRepository.fetchHotfixProfileOnce().getOrNull()?.let { profile ->
            app.otaUpdateManager.stageCloudHotfix(profile, notifyUser = true)
          }
          app.cloudSyncRepository.fetchAppControlConfigOnce().getOrNull()?.let { cfg ->
            if (cfg.otaFeedUrl.isNotBlank()) {
              app.otaUpdateManager.checkFeedForUpdates(cfg.otaFeedUrl, notifyUser = true)
            }
          }
          app.cloudSyncRepository.touchLastSeen(user.uid)
        } else {
          // 2. Also check optional JSON OTA feed URL if set locally
          val settings = app.repository.getCurrentSettings()
          if (settings.otaFeedUrl.isNotBlank()) {
            app.otaUpdateManager.checkFeedForUpdates(settings.otaFeedUrl, notifyUser = true)
          }
        }
        Result.success()
      }
      .getOrElse { Result.retry() }
  }

  companion object {
    private const val PERIODIC_WORK_NAME = "live_translator_hotfix_profile_check"
    private const val IMMEDIATE_WORK_NAME = "live_translator_hotfix_immediate_check"

    fun schedulePeriodic(context: Context) {
      runCatching {
        if (!WorkManager.isInitialized()) {
          WorkManager.initialize(
            context,
            androidx.work.Configuration.Builder().build(),
          )
        }
        val constraints =
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest =
          PeriodicWorkRequestBuilder<HotfixBackgroundWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
          .enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
          )
      }
    }

    fun triggerImmediateCheck(context: Context) {
      runCatching {
        if (!WorkManager.isInitialized()) {
          WorkManager.initialize(
            context,
            androidx.work.Configuration.Builder().build(),
          )
        }
        val constraints =
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request =
          OneTimeWorkRequestBuilder<HotfixBackgroundWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
          .enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
          )
      }
    }
  }
}

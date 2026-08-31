package pk.vancott.tenders.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import pk.vancott.tenders.MainActivity
import pk.vancott.tenders.R
import pk.vancott.tenders.data.TenderRepository
import java.util.concurrent.TimeUnit

/**
 * Background check for new SMD tenders.
 *
 * This is a pull, not a push: the phone asks the feed every couple of hours.
 * That needs no server, no Firebase account and no per-message cost - which
 * matters because the scraper itself runs on free infrastructure. The trade is
 * latency: you hear within a couple of hours rather than instantly. Since the
 * scraper only refreshes every 30 minutes and tenders run for weeks, that is
 * the right trade.
 */
class TenderSyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = TenderRepository(context)
        val feed = runCatching { repo.refresh() }.getOrNull()
            ?: return Result.retry()      // offline or feed down: try again later

        val fresh = repo.unseenSmd(feed)
        if (fresh.isNotEmpty()) {
            notify(fresh.size, fresh.first().title)
            repo.markSeen(fresh)
        }
        return Result.success()
    }

    private fun notify(count: Int, firstTitle: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New SMD tenders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Alerts when a new SMD or LED tender is published" }
            )
        }

        // Android 13+ can refuse to post without the runtime permission; check
        // rather than crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = if (count == 1) firstTitle
        else count.toString() + " new SMD tenders — including " + firstTitle

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tender)
            .setContentTitle(if (count == 1) "New SMD tender" else count.toString() + " new SMD tenders")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }

    companion object {
        private const val CHANNEL = "smd_tenders"
        private const val NOTIF_ID = 4201
        private const val WORK = "tender-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TenderSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            // KEEP, not REPLACE: replacing on every app launch would reset the
            // interval and mean the check effectively never runs.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }
    }
}

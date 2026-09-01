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
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderRepository
import pk.vancott.tenders.data.UserDataStore
import pk.vancott.tenders.data.matches
import java.util.concurrent.TimeUnit

/**
 * Background checks: new matching tenders, and deadlines coming up.
 *
 * This is a pull, not a push: the phone asks the feed every couple of hours.
 * That needs no notification server, no Google account and no per-message cost.
 * The trade is latency - you hear within a couple of hours rather than instantly
 * - and since tenders stay open for weeks, that is the right trade.
 */
class TenderSyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = TenderRepository(context)
        val store = UserDataStore(context)

        val feed = try {
            repo.refresh()
        } catch (e: TenderRepository.NotModified) {
            // Nothing new upstream: a few hundred bytes, no parsing, no retry.
            // Deadlines still need checking against what we already hold.
            repo.cachedFeed()?.let { checkDeadlines(it.tenders, store) }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()          // offline or feed down: try later
        }

        // 1. New SMD tenders (the built-in filter).
        val freshSmd = repo.unseenSmd(feed)
        if (freshSmd.isNotEmpty()) {
            notify(
                NOTIF_SMD,
                if (freshSmd.size == 1) "New SMD tender" else freshSmd.size.toString() + " new SMD tenders",
                if (freshSmd.size == 1) freshSmd.first().title
                else freshSmd.size.toString() + " new SMD tenders — including " + freshSmd.first().title,
            )
            repo.markSeen(freshSmd)
        }

        // 2. The user's own keyword alerts.
        val watched = store.searches().filter { it.notify }
        if (watched.isNotEmpty()) {
            val seen = store.load().notes.keys
            watched.forEach { search ->
                val hits = feed.tenders.filter {
                    !it.isClosed && it.matches(search.query) && it.uid !in seen
                }
                if (hits.isNotEmpty()) {
                    notify(
                        NOTIF_SEARCH_BASE + search.id.takeLast(4).hashCode(),
                        "\"" + search.name + "\": " + hits.size + " new",
                        hits.first().title,
                    )
                }
            }
        }

        checkDeadlines(feed.tenders, store)
        return Result.success()
    }

    /** Reminds about shortlisted tenders whose deadline is approaching. */
    private fun checkDeadlines(tenders: List<Tender>, store: UserDataStore) {
        val notes = store.load().notes
        tenders.forEach { t ->
            val n = notes[t.uid] ?: return@forEach
            if (n.remindDaysBefore <= 0) return@forEach
            val left = t.daysLeft ?: return@forEach
            if (left in 0..n.remindDaysBefore.toLong()) {
                notify(
                    NOTIF_DEADLINE_BASE + t.uid.takeLast(4).hashCode(),
                    if (left == 0L) "Closes today" else "Closes in " + left + " days",
                    t.title,
                )
            }
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Tender alerts", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "New matching tenders and approaching deadlines"
                        enableVibration(true)
                    }
            )
        }

        // Android 13+ refuses to post without the runtime permission; check
        // rather than crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tender)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // sound and vibration
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    companion object {
        private const val CHANNEL = "tender_alerts"
        private const val NOTIF_SMD = 4201
        private const val NOTIF_SEARCH_BASE = 5000
        private const val NOTIF_DEADLINE_BASE = 6000
        private const val WORK = "tender-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TenderSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        // Never wake the phone to check tenders on a low
                        // battery - nothing here is that urgent.
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            // KEEP, not REPLACE: replacing on every launch would reset the
            // interval and mean the check effectively never runs.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }
    }
}

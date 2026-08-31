package pk.vancott.tenders.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pk.vancott.tenders.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the tender feed and keeps a copy on disk.
 *
 * Offline-first on purpose: staff open this on site, on bad mobile data. The
 * cached feed loads instantly and a refresh happens behind it. A failed refresh
 * never blanks the screen - you keep yesterday's tenders and are told the
 * refresh failed, which is far more useful than an empty list.
 */
class TenderRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheFile: File get() = File(context.filesDir, "tenders.json")
    private val seenFile: File get() = File(context.filesDir, "seen_uids.txt")

    /**
     * The feed to show right now: the downloaded copy if there is one,
     * otherwise the snapshot shipped inside the APK.
     *
     * The bundled copy is what makes the app useful the moment it is installed
     * - before any GitHub feed exists, and on a phone with no signal.
     */
    fun cachedFeed(): TenderFeed? = downloadedFeed() ?: bundledFeed()

    private fun downloadedFeed(): TenderFeed? =
        runCatching {
            if (!cacheFile.exists()) null
            else json.decodeFromString<TenderFeed>(cacheFile.readText())
        }.getOrNull()

    private fun bundledFeed(): TenderFeed? =
        runCatching {
            context.assets.open("tenders.json").bufferedReader().use {
                json.decodeFromString<TenderFeed>(it.readText())
            }
        }.getOrNull()

    val lastUpdated: Long get() = if (cacheFile.exists()) cacheFile.lastModified() else 0L

    /** Downloads a fresh feed and caches it. Throws on failure so the caller
     *  can report *why* rather than silently showing stale data. */
    suspend fun refresh(): TenderFeed = withContext(Dispatchers.IO) {
        val conn = (URL(BuildConfig.FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VancottTenders/1.0 (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("Server returned ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val feed = json.decodeFromString<TenderFeed>(body)
            // Write only after a successful parse, so a truncated download can
            // never replace a good cache with a broken one.
            cacheFile.writeText(body)
            feed
        } finally {
            conn.disconnect()
        }
    }

    // --- "new since you last looked" -------------------------------------
    // Tracked on the device rather than in the feed, because each employee
    // opens the app at different times and "new" means new *to them*.

    private fun seenUids(): MutableSet<String> =
        runCatching { seenFile.readLines().filter { it.isNotBlank() }.toMutableSet() }
            .getOrDefault(mutableSetOf())

    fun unseenSmd(feed: TenderFeed): List<Tender> {
        val seen = seenUids()
        return feed.tenders.filter { it.isSmd && !it.isClosed && it.uid !in seen }
    }

    fun markSeen(tenders: List<Tender>) {
        if (tenders.isEmpty()) return
        val all = seenUids().apply { addAll(tenders.map { it.uid }) }
        runCatching { seenFile.writeText(all.joinToString("\n")) }
    }
}

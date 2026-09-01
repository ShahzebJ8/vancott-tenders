package pk.vancott.tenders.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pk.vancott.tenders.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Fetches the tender feed and keeps a copy on disk.
 *
 * Offline-first on purpose: staff open this on site, on bad mobile data. The
 * saved copy loads instantly and a refresh happens behind it. A failed refresh
 * never blanks the screen - you keep the tenders you had, and are told the
 * refresh failed.
 */
private val NEWS_URL =
    BuildConfig.FEED_URL.substringBeforeLast("/") + "/news.json"

private val AWARDS_URL =
    BuildConfig.FEED_URL.substringBeforeLast("/") + "/awards.json"


class TenderRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheFile: File get() = File(context.filesDir, "tenders.json")
    private val etagFile: File get() = File(context.filesDir, "tenders.etag")
    private val seenFile: File get() = File(context.filesDir, "seen_uids.txt")
    private val newsFile: File get() = File(context.filesDir, "news.json")
    private val awardsFile: File get() = File(context.filesDir, "awards.json")

    class NotModified : Exception("Already up to date")

    /**
     * The feed to show right now: the downloaded copy if there is one,
     * otherwise the snapshot shipped inside the APK.
     *
     * The bundled copy is what makes the app useful the moment it is installed,
     * and on a phone with no signal.
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

    /**
     * Downloads a fresh feed, but only if it actually changed.
     *
     * The server is told which version we already hold (ETag). If nothing has
     * changed since, it replies "304" with an empty body - a few hundred bytes
     * instead of three megabytes. Since the scraper only finds new tenders a few
     * times a day, most background checks now cost almost no data and almost no
     * battery.
     *
     * Throws NotModified when there is nothing new, so callers can tell
     * "unchanged" apart from "failed".
     */
    suspend fun refresh(): TenderFeed = withContext(Dispatchers.IO) {
        val conn = (URL(BuildConfig.FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "TenderDesk/1.0 (Android)")
            savedEtag()?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                throw NotModified()
            }
            if (conn.responseCode !in 200..299) {
                error("Server returned " + conn.responseCode)
            }

            val raw = conn.inputStream.let {
                if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(it) else it
            }
            val body = raw.bufferedReader().use { it.readText() }

            val feed = json.decodeFromString<TenderFeed>(body)
            // Written only after a successful parse, so a truncated download can
            // never replace a good copy with a broken one.
            cacheFile.writeText(body)
            conn.getHeaderField("ETag")?.let { runCatching { etagFile.writeText(it) } }
            feed
        } finally {
            conn.disconnect()
        }
    }

    private fun savedEtag(): String? =
        runCatching { if (etagFile.exists()) etagFile.readText().trim() else null }.getOrNull()

    // --- news ------------------------------------------------------------
    // A separate, much smaller file. Kept apart from the tender feed so that
    // news being unavailable can never stop tenders loading, and so refreshing
    // news costs a few kilobytes rather than three megabytes.

    fun cachedNews(): NewsFeed? =
        runCatching {
            if (newsFile.exists()) json.decodeFromString<NewsFeed>(newsFile.readText())
            else context.assets.open("news.json").bufferedReader().use {
                json.decodeFromString<NewsFeed>(it.readText())
            }
        }.getOrNull()

    suspend fun refreshNews(): NewsFeed = withContext(Dispatchers.IO) {
        val conn = (URL(NEWS_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "TenderDesk/1.0 (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) error("Server returned " + conn.responseCode)
            val raw = conn.inputStream.let {
                if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(it) else it
            }
            val body = raw.bufferedReader().use { it.readText() }
            val feed = json.decodeFromString<NewsFeed>(body)
            newsFile.writeText(body)
            feed
        } finally {
            conn.disconnect()
        }
    }

    // --- market data -------------------------------------------------------
    // Contract awards and bid evaluations. Another separate file, for the same
    // reason as news: it must never be able to stop tenders from loading.

    fun cachedAwards(): AwardFeed? =
        runCatching {
            if (awardsFile.exists()) json.decodeFromString<AwardFeed>(awardsFile.readText())
            else context.assets.open("awards.json").bufferedReader().use {
                json.decodeFromString<AwardFeed>(it.readText())
            }
        }.getOrNull()

    suspend fun refreshAwards(): AwardFeed = withContext(Dispatchers.IO) {
        val conn = (URL(AWARDS_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "TenderDesk/1.0 (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) error("Server returned " + conn.responseCode)
            val raw = conn.inputStream.let {
                if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(it) else it
            }
            val body = raw.bufferedReader().use { it.readText() }
            val feed = json.decodeFromString<AwardFeed>(body)
            awardsFile.writeText(body)
            feed
        } finally {
            conn.disconnect()
        }
    }

    // --- "new since you last looked" -------------------------------------
    // Tracked on the device, because each employee opens the app at different
    // times and "new" means new to them.

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

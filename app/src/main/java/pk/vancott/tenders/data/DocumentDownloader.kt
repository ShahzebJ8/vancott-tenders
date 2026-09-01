package pk.vancott.tenders.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/**
 * Saves a tender's published documents to the phone's Downloads folder.
 *
 * Android's own DownloadManager does the work: it survives the app being
 * closed, retries on a dropped connection, and shows progress in the notification
 * shade - all things a hand-rolled download would get wrong on bad mobile data.
 */
object DocumentDownloader {

    /** Folder inside Downloads, one per tender, so a set stays together. */
    private fun folderFor(t: Tender): String {
        val name = (t.tenderNo ?: t.uid).replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "VANCOTT Tenders/" + name
    }

    private fun fileNameFor(t: Tender, url: String, index: Int, total: Int): String {
        // Prefer the real published filename, which PPRA encodes in the link.
        val decoded = decodePpraFileName(url)
        if (decoded != null) return decoded
        val ref = (t.tenderNo ?: "document").replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (total == 1) ref + ".pdf" else ref + "_" + (index + 1) + ".pdf"
    }

    /**
     * PPRA document links carry the stored path as base64: /pdf?file=<base64>.
     * Decoding it gives the real document name instead of "document_1.pdf".
     */
    private fun decodePpraFileName(url: String): String? = runCatching {
        val encoded = Uri.parse(url).getQueryParameter("file") ?: return null
        val path = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        val name = path.substringAfterLast('/')
        if (name.isBlank()) null
        else name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
    }.getOrNull()

    /**
     * Queues every document for this tender. Returns how many were queued.
     * Returns 0 when the tender has no published documents - the caller says so
     * rather than pretending something is downloading.
     */
    fun downloadAll(context: Context, t: Tender): Int {
        if (t.docUrls.isEmpty()) return 0
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return 0

        var queued = 0
        t.docUrls.forEachIndexed { i, url ->
            runCatching {
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileNameFor(t, url, i, t.docUrls.size))
                    .setDescription(t.title.take(60))
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        folderFor(t) + "/" + fileNameFor(t, url, i, t.docUrls.size),
                    )
                    .setAllowedOverRoaming(true)
                    .setAllowedOverMetered(true)   // mobile data is the norm on site
                dm.enqueue(req)
                queued++
            }
        }
        return queued
    }

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}

package pk.vancott.tenders.ui

import pk.vancott.tenders.data.Tender

/** The sections in the slide-out menu. */
enum class Section(val label: String) {
    TENDERS("Tenders"),
    SHORTLIST("Shortlist"),
    ALERTS("Keyword alerts"),
    MARKET("Market"),
    NEWS("News"),
    ECONOMY("Economy"),
    ABOUT("About"),
}

/**
 * The toggle bar under the search box.
 *
 * These are the states the portals themselves publish in, so a tender is only
 * ever placed in one of them by what the source said - never by our judgement.
 */
enum class Stage(val label: String) {
    ACTIVE("Active"),
    PREQUAL("Pre-qualification"),
    EXPIRED("Expired"),
}

/**
 * Which stage a tender belongs to.
 *
 * Pre-qualification is checked first: a PQ notice that is still open is a
 * pre-qualification, not a plain active tender, and lumping the two together
 * hides the fact that you must qualify before you can bid at all.
 */
fun stageOf(t: Tender): Stage = when {
    t.isClosed -> Stage.EXPIRED
    t.isPrequalification -> Stage.PREQUAL
    else -> Stage.ACTIVE
}

/** Buckets for the grouped list. Ordered by how soon you must act. */
enum class Bucket(val label: String) {
    TODAY("Closing today"),
    THIS_WEEK("Closing this week"),
    THIS_MONTH("Closing this month"),
    LATER("Later"),
    NO_DATE("No closing date given"),
    CLOSED("Closed"),
}

fun bucketOf(t: Tender): Bucket {
    if (t.isClosed) return Bucket.CLOSED
    val d = t.daysLeft ?: return Bucket.NO_DATE
    return when {
        d <= 0L -> Bucket.TODAY
        d <= 7L -> Bucket.THIS_WEEK
        d <= 30L -> Bucket.THIS_MONTH
        else -> Bucket.LATER
    }
}

/**
 * Short initials for a department, used as the small block on each row.
 *
 * Prefers an abbreviation the source already printed in brackets - "Sui
 * Northern Gas Pipelines Limited (SNGPL)" becomes SNGPL, which is what people
 * actually call it - and only falls back to building initials.
 */
fun initialsFor(organisation: String?): String {
    if (organisation.isNullOrBlank()) return "—"
    Regex("\\(([A-Z]{2,6})\\)").find(organisation)?.let { return it.groupValues[1] }

    val skip = setOf("of", "the", "and", "for", "de", "&")
    val words = organisation.split(Regex("[^A-Za-z]+"))
        .filter { it.isNotBlank() && it.lowercase() !in skip }
    if (words.isEmpty()) return "—"
    if (words.size == 1) return words[0].take(3).uppercase()
    return words.take(3).joinToString("") { it.first().uppercase() }
}

package pk.vancott.tenders.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Mirrors one record in tenders.json exactly.
 *
 * Nearly every field is nullable on purpose. The scraper never invents a value:
 * if PPRA did not print a closing date or a city, it arrives as null and the UI
 * says "not stated". A blank here is information, not a bug.
 */
@Serializable
data class Tender(
    val uid: String = "",
    val source: String = "",
    @SerialName("source_name") val sourceName: String = "",
    val url: String = "",
    val title: String = "",
    val organisation: String? = null,
    @SerialName("tender_no") val tenderNo: String? = null,
    val city: String? = null,
    val province: String? = null,
    val published: String? = null,
    val closing: String? = null,
    val value: String? = null,
    val category: String? = null,
    val description: String? = null,
    @SerialName("doc_urls") val docUrls: List<String> = emptyList(),
    @SerialName("is_smd") val isSmd: Boolean = false,
    @SerialName("smd_score") val smdScore: Int = 0,
    @SerialName("matched_terms") val matchedTerms: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("first_seen") val firstSeen: String = "",
    val detail: Map<String, String> = emptyMap(),
) {
    /**
     * Days until closing. Null when the source never stated a closing date.
     *
     * Computed ONCE per tender, not on every read. It used to be a plain getter,
     * which meant parsing a date string again for every row on every scroll
     * frame and every keystroke - 3,000 date parses per sort, several times a
     * second. That was the scrolling and typing lag.
     */
    val daysLeft: Long? by lazy {
        closing?.take(10)?.let {
            runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it)) }.getOrNull()
        }
    }

    val isClosed: Boolean by lazy { (daysLeft ?: 1L) < 0L }

    /** Sort key for "closing soonest"; undated tenders sort last. */
    val closingSortKey: Long by lazy { daysLeft ?: Long.MAX_VALUE }

    /**
     * A pre-qualification notice: you are applying to be allowed to bid later,
     * not bidding now.
     *
     * Decided only from words the source printed - its own tender type, its
     * tags, or the title. Never inferred from anything else, because telling
     * someone a live tender is "only a pre-qualification" would make them skip
     * a real opportunity.
     */
    val isPrequalification: Boolean by lazy {
        val text = listOfNotNull(
            detail["tender_type"], category, title,
        ).joinToString(" ").lowercase() + " " + tags.joinToString(" ").lowercase()
        Regex("pre-?qualification|\\bpq\\b|\\beoi\\b|expression of interest")
            .containsMatchIn(text)
    }

    /** Everything a free-text search should look through. */
    val haystack: String by lazy {
        listOfNotNull(
            title, organisation, tenderNo, city, province, category, description,
            detail["office_name"], detail["office_address"], detail["reference_no"],
        ).joinToString(" ").lowercase()
    }
}

@Serializable
data class SourceStatus(
    val name: String = "",
    val status: String = "",
    val scraped: Int? = null,
    val new: Int? = null,
    val note: String? = null,
    val checked: String? = null,
)

@Serializable
data class TenderFeed(
    val generated: String = "",
    val count: Int = 0,
    @SerialName("smd_count") val smdCount: Int = 0,
    val sources: Map<String, SourceStatus> = emptyMap(),
    val tenders: List<Tender> = emptyList(),
)

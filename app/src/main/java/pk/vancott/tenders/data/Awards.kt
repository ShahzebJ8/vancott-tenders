package pk.vancott.tenders.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A contract that was actually awarded. Mirrors data/awards.json.
 *
 * `valueText` is what PPRA printed, verbatim. `valuePkr` is set only where a
 * clean figure could be read; where a contract is published only as a band
 * ("< Rs. 50 Million") it stays null and `valueBand` carries the band. The
 * distinction matters: this data exists to tell you what work sells for, and a
 * guessed number would defeat it.
 */
@Serializable
data class Award(
    @SerialName("contract_no") val contractNo: String? = null,
    val reference: String? = null,
    val title: String? = null,
    val organisation: String? = null,
    val office: String? = null,
    val winner: String? = null,
    @SerialName("value_text") val valueText: String? = null,
    @SerialName("value_pkr") val valuePkr: Long? = null,
    @SerialName("value_band") val valueBand: String? = null,
    val awarded: String? = null,
    val city: String? = null,
    val province: String? = null,
    val url: String? = null,
    @SerialName("doc_url") val docUrl: String? = null,
    @SerialName("is_smd") val isSmd: Boolean = false,
    @SerialName("matched_terms") val matchedTerms: List<String> = emptyList(),
) {
    val haystack: String by lazy {
        listOfNotNull(title, organisation, winner, city, contractNo, reference)
            .joinToString(" ").lowercase()
    }
}

/**
 * A published bid evaluation: how many firms bid, and who was lowest.
 * More of these are published than awards, so it is the better view of who
 * you are competing against.
 */
@Serializable
data class Evaluation(
    @SerialName("evaluation_no") val evaluationNo: String? = null,
    @SerialName("tender_no") val tenderNo: String? = null,
    val reference: String? = null,
    val title: String? = null,
    val organisation: String? = null,
    @SerialName("bid_count") val bidCount: Int? = null,
    val lowest: List<String> = emptyList(),
    @SerialName("evaluation_type") val evaluationType: String? = null,
    val advertised: String? = null,
    val city: String? = null,
    val province: String? = null,
    val url: String? = null,
    @SerialName("is_smd") val isSmd: Boolean = false,
) {
    val haystack: String by lazy {
        (listOfNotNull(title, organisation, tenderNo) + lowest)
            .joinToString(" ").lowercase()
    }
}

@Serializable
data class PriceBand(
    val term: String = "",
    val awards: Int = 0,
    val low: Long = 0,
    val median: Long = 0,
    val high: Long = 0,
)

@Serializable
data class AwardFeed(
    val generated: String = "",
    val count: Int = 0,
    @SerialName("with_value") val withValue: Int = 0,
    @SerialName("with_winner") val withWinner: Int = 0,
    @SerialName("evaluation_count") val evaluationCount: Int = 0,
    @SerialName("price_history") val priceHistory: List<PriceBand> = emptyList(),
    val awards: List<Award> = emptyList(),
    val evaluations: List<Evaluation> = emptyList(),
)

/** Rupee figures, grouped the way they are spoken about in Pakistan. */
fun formatPkr(value: Long?): String {
    if (value == null) return "—"
    return when {
        value >= 10_000_000 -> "Rs " + trim(value / 10_000_000.0) + " crore"
        value >= 100_000 -> "Rs " + trim(value / 100_000.0) + " lac"
        else -> "Rs " + "%,d".format(value)
    }
}

private fun trim(v: Double): String =
    if (v >= 100) "%.0f".format(v) else "%.2f".format(v).trimEnd('0').trimEnd('.')

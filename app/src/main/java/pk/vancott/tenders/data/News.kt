package pk.vancott.tenders.data

import kotlinx.serialization.Serializable

/**
 * One headline. Mirrors data/news.json.
 *
 * Only a headline, a short extract and a link are ever stored - the article
 * itself stays with the publisher, which is both the legal position and the
 * reason every story opens on their site.
 */
@Serializable
data class Story(
    val title: String = "",
    val url: String = "",
    val source: String = "",
    val published: String? = null,
    val summary: String? = null,
    val image: String? = null,
    val topics: List<String> = emptyList(),
    val relevant: Boolean = false,
    /** About Pakistan's own economy, rather than a foreign market. */
    val domestic: Boolean = false,
)

@Serializable
data class NewsFeed(
    val generated: String = "",
    val count: Int = 0,
    val stories: List<Story> = emptyList(),
)

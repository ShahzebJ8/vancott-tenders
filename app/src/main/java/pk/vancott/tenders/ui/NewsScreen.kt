package pk.vancott.tenders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.Story
import pk.vancott.tenders.ui.theme.*
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Business and economy headlines.
 *
 * Every story shows the publisher's name and opens on the publisher's own site.
 * What is stored is only the headline, a short extract and the link - which is
 * exactly what a syndication feed is published for. The full article is never
 * copied, so nobody's work is being taken and there is nothing to argue about.
 */
@Composable
fun NewsScreen(vm: TenderViewModel, onMenu: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var detailed by remember { mutableStateOf(true) }
    var topic by remember { mutableStateOf<String?>(null) }
    var relevantOnly by remember { mutableStateOf(false) }
    var publisher by remember { mutableStateOf<String?>(null) }
    // Pakistan first by default: foreign market news matters less to a bid.
    var pakistanOnly by remember { mutableStateOf(true) }

    val stories = s.news?.stories.orEmpty()
    val topics = remember(stories) {
        stories.flatMap { it.topics }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val publishers = remember(stories) {
        stories.map { it.source }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val shown = remember(stories, topic, relevantOnly, publisher, pakistanOnly) {
        stories.filter {
            (topic == null || topic in it.topics) &&
                (!relevantOnly || it.relevant) &&
                (publisher == null || it.source == publisher) &&
                (!pakistanOnly || it.domestic)
        }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(
            title = "News",
            subtitle = "${shown.size} business stories from ${publishers.size} publishers",
            onMenu = onMenu,
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Pakistan", pakistanOnly) { pakistanOnly = !pakistanOnly }
            Chip("For this business", relevantOnly, SmdAccent) { relevantOnly = !relevantOnly }
            Chip(if (detailed) "Detailed" else "Quick", false) { detailed = !detailed }
        }

        if (stories.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No news loaded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp)) {

            item(key = "publishers") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("All publishers", publisher == null) { publisher = null }
                    publishers.forEach { (name, n) ->
                        Chip("$name $n", publisher == name) {
                            publisher = if (publisher == name) null else name
                        }
                    }
                }
            }

            item(key = "topics") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("All topics", topic == null) { topic = null }
                    topics.forEach { (name, n) ->
                        Chip("$name $n", topic == name) {
                            topic = if (topic == name) null else name
                        }
                    }
                }
            }

            items(shown, key = { it.url }) { story ->
                StoryRow(story, detailed) { open(story.url) }
                NewsHairline()
            }

            item(key = "credit") {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("SOURCES", style = Hud, color = InkFaint)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Headlines are taken from the public news feeds published by " +
                            publishers.joinToString(", ") { it.first } + ". " +
                            "Each story shows its publisher and opens on that publisher's " +
                            "own website, where the full article and any advertising " +
                            "belongs to them. Only the headline and a short extract are " +
                            "shown here.\n\n" +
                            "Only business and economic reporting is carried. Crime, " +
                            "sport, weather and entertainment are filtered out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, detailed: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Publisher first and in the brand colour: the reader should know whose
        // reporting this is before they read the headline.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.source.uppercase(), style = Hud, color = BrandLit)
            story.published?.let {
                Text("   " + relativeTime(it), style = MaterialTheme.typography.labelSmall,
                     color = InkFaint)
            }
            if (story.relevant) {
                Spacer(Modifier.width(10.dp))
                Tag("For you", SmdAccent)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            story.title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = if (detailed) 4 else 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (detailed && !story.summary.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(story.summary, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Read on " + story.source,
            style = MaterialTheme.typography.labelSmall,
            color = BrandLit,
        )

        if (story.topics.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                story.topics.take(3).forEach { Tag(it, InkMuted) }
            }
        }
    }
}

@Composable
private fun NewsHairline() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Hairline))
}

/** "3h ago" reads faster than a timestamp when you are scanning. */
fun relativeTime(iso: String): String = runCatching {
    val then = OffsetDateTime.parse(iso)
    val mins = Duration.between(then, OffsetDateTime.now()).toMinutes()
    when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d ago"
        else -> iso.take(10)
    }
}.getOrDefault(iso.take(10))

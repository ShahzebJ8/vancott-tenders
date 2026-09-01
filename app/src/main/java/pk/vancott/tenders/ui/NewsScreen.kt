package pk.vancott.tenders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Headline, a short extract and a link - the article itself opens on the
 * publisher's site. That is the legal position for syndicated feeds, and it is
 * also the honest one: this is a pointer to their reporting, not a copy of it.
 *
 * "Quick" is the scan view; "Detailed" adds the extract. Both are the same
 * stories, so switching never hides anything.
 */
@Composable
fun NewsScreen(vm: TenderViewModel, onMenu: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var detailed by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<String?>(null) }
    var relevantOnly by remember { mutableStateOf(false) }

    val stories = s.news?.stories.orEmpty()
    val topics = remember(stories) {
        stories.flatMap { it.topics }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val shown = remember(stories, topic, relevantOnly) {
        stories.filter { (topic == null || topic in it.topics) && (!relevantOnly || it.relevant) }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(
            title = "News",
            subtitle = "${shown.size} stories" +
                (s.news?.generated?.let { "  ·  updated " + it.take(10) } ?: ""),
            onMenu = onMenu,
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Quick", !detailed) { detailed = false }
            Chip("Detailed", detailed) { detailed = true }
            Chip("For this business", relevantOnly, SmdAccent) { relevantOnly = !relevantOnly }
        }

        if (stories.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No news loaded yet.\nPull refresh on the Tenders screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)) {
                if (topics.isNotEmpty()) {
                    item(key = "topics") {
                        Row(
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Chip("All", topic == null) { topic = null }
                            topics.forEach { (name, n) ->
                                Chip("$name $n", topic == name) {
                                    topic = if (topic == name) null else name
                                }
                            }
                        }
                    }
                }
                items(shown, key = { it.url }) { story ->
                    StoryRow(story, detailed) { open(story.url) }
                    RowDivider()
                }
            }
        }
    }
}

@Composable
private fun StoryRow(story: Story, detailed: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.source, style = MaterialTheme.typography.labelSmall, color = BrandLit)
            story.published?.let {
                Text("  ·  " + relativeTime(it), style = MaterialTheme.typography.labelSmall,
                     color = InkFaint)
            }
            if (story.relevant) {
                Spacer(Modifier.width(8.dp))
                Tag("For you", SmdAccent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            story.title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = if (detailed) 4 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detailed && !story.summary.isNullOrBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(story.summary, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        if (story.topics.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                story.topics.take(3).forEach { Tag(it, InkMuted) }
            }
        }
    }
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

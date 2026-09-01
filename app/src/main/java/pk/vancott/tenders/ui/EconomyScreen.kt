package pk.vancott.tenders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.ui.theme.*

/**
 * Economic developments — the money side of what drives tenders.
 *
 * The stories here are the ones tagged budget, construction, energy or economy:
 * government spending decisions are what turn into tenders three months later,
 * so this is an early view of where work is coming from.
 *
 * Live indicators (policy rate, inflation, rupee, KSE-100) are not shown yet.
 * They need a data source we can rely on, and a wrong number here would be
 * worse than no number - so the section says so rather than showing a guess.
 */
@Composable
fun EconomyScreen(vm: TenderViewModel, onMenu: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    val economic = remember(s.news) {
        s.news?.stories.orEmpty().filter { story ->
            story.topics.any {
                it in setOf("Budget & spending", "Construction & projects",
                            "Energy & power", "Economy")
            }
        }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(
            title = "Economy",
            subtitle = "${economic.size} developments that affect spending",
            onMenu = onMenu,
        )

        if (economic.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Nothing loaded yet.", style = MaterialTheme.typography.bodyMedium,
                     color = InkMuted)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
            item(key = "why") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Panel).padding(16.dp)
                ) {
                    Text("Why this matters", style = MaterialTheme.typography.labelMedium,
                         color = BrandLit)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Government spending decisions become tenders months later. " +
                            "A budget allocation or an approved project is the earliest " +
                            "warning that work is coming.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
            }

            items(economic, key = { it.url }) { story ->
                Column(
                    Modifier.fillMaxWidth().clickable { open(story.url) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(story.source, style = MaterialTheme.typography.labelSmall,
                             color = BrandLit)
                        story.published?.let {
                            Text("  ·  " + relativeTime(it),
                                 style = MaterialTheme.typography.labelSmall, color = InkFaint)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(story.title, style = MaterialTheme.typography.titleMedium, color = Ink,
                         maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!story.summary.isNullOrBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(story.summary, style = MaterialTheme.typography.bodyMedium,
                             color = InkMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        story.topics.take(3).forEach { Tag(it, InkMuted) }
                    }
                }
                RowDivider()
            }
        }
    }
}

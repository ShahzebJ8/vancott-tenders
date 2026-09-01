package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.ui.theme.*

/**
 * Where the data comes from, and where it does not.
 *
 * This screen exists so nobody has to guess whether an empty province means
 * "no tenders" or "we could not reach it". Every source reports its own status.
 */
@Composable
fun AboutScreen(s: UiState, onMenu: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(title = "About", subtitle = "Where this data comes from", onMenu = onMenu)

        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 40.dp)
        ) {
            Card {
                Text("Tenders", style = MaterialTheme.typography.labelMedium, color = BrandLit)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${s.feed?.count ?: 0} tenders, ${s.feed?.smdCount ?: 0} flagged as SMD or LED.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                )
                s.feed?.generated?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Collected $it", style = MaterialTheme.typography.labelSmall,
                         color = InkFaint)
                }
            }

            Card {
                Text("Sources", style = MaterialTheme.typography.labelMedium, color = BrandLit)
                Spacer(Modifier.height(8.dp))
                val sources = s.feed?.sources.orEmpty()
                if (sources.isEmpty()) {
                    Text("No source information in this copy.",
                         style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                } else {
                    sources.forEach { (_, status) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(status.name, style = MaterialTheme.typography.bodyMedium,
                                 color = Ink, modifier = Modifier.weight(1f))
                            Text(
                                when (status.status) {
                                    "ok" -> "${status.scraped ?: 0}"
                                    "blocked" -> "needs a manual check"
                                    else -> "unavailable"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status.status == "ok") SmdAccent else Soon,
                            )
                        }
                    }
                }
            }

            Card {
                Text("Not covered", style = MaterialTheme.typography.labelMedium, color = BrandLit)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sindh, KP, Balochistan, AJK and Gilgit-Baltistan run their own portals. " +
                        "Their shared system currently returns errors on the government side, " +
                        "so their tenders are not listed here. Federal tenders for those " +
                        "provinces still appear.",
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
            }

            Card {
                Text("Accuracy", style = MaterialTheme.typography.labelMedium, color = BrandLit)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nothing here is invented. A field the portal did not publish is left " +
                        "blank rather than filled in. Terms read out of tender documents show " +
                        "the page and the exact line they came from. None of it replaces " +
                        "reading the official documents before you bid.",
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
            }

            Card {
                Text("Privacy", style = MaterialTheme.typography.labelMedium, color = BrandLit)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This app collects nothing about you. Your shortlist, notes and " +
                        "assignments stay on this phone and are excluded from cloud backup. " +
                        "The only thing it downloads is the public tender list.",
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 7.dp)
            .clip(RoundedCornerShape(12.dp)).background(Panel).padding(16.dp),
        content = content,
    )
}

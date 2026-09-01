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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.Award
import pk.vancott.tenders.data.Evaluation
import pk.vancott.tenders.data.formatPkr
import pk.vancott.tenders.ui.theme.*

private enum class IntelView(val label: String) {
    AWARDS("Awarded"),
    EVALUATIONS("Bid results"),
    PRICES("Price history"),
}

/**
 * What work actually sold for, and who won it.
 *
 * A tender says what a department wants. This says what they paid and to whom -
 * which is the difference between knowing an opportunity exists and knowing
 * what to bid on it.
 *
 * Laid out the way the VANCOTT site sets a page: a tracked HUD label marking
 * each block, a large light figure carrying the number, and hairlines instead
 * of boxes. The figure is the content; everything around it stays quiet.
 */
@Composable
fun IntelScreen(vm: TenderViewModel, onMenu: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var view by remember { mutableStateOf(IntelView.AWARDS) }
    var query by remember { mutableStateOf("") }

    val feed = s.awards
    val words = remember(query) {
        query.trim().lowercase().split(' ').filter { it.isNotBlank() }
    }
    val awards = remember(feed, words) {
        feed?.awards.orEmpty().filter { a -> words.all { a.haystack.contains(it) } }
    }
    val evaluations = remember(feed, words) {
        feed?.evaluations.orEmpty().filter { e -> words.all { e.haystack.contains(it) } }
    }

    fun open(url: String?) {
        url ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(
            title = "Market",
            subtitle = feed?.let {
                "${it.count} awards  ·  ${it.evaluationCount} bid results"
            } ?: "Not loaded yet",
            onMenu = onMenu,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IntelView.entries.forEach { v ->
                Chip(v.label, view == v) { view = v }
            }
        }

        if (view != IntelView.PRICES) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = {
                    Text("Search a firm, buyer or item",
                         style = MaterialTheme.typography.bodyMedium, color = InkFaint)
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = InkMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, "Clear", tint = InkMuted)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Hairline,
                    focusedContainerColor = Void,
                    unfocusedContainerColor = Void,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    cursorColor = BrandLit,
                ),
            )
        }

        if (feed == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No market data loaded yet.",
                     style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 44.dp)) {
            when (view) {
                IntelView.AWARDS -> {
                    item { HudLabel("Contracts awarded", awards.size) }
                    items(awards, key = { it.contractNo ?: (it.title ?: "") + it.awarded }) { a ->
                        AwardRow(a) { open(a.url ?: a.docUrl) }
                        Hairline()
                    }
                }

                IntelView.EVALUATIONS -> {
                    item { HudLabel("Who bid, and who was lowest", evaluations.size) }
                    items(evaluations, key = { it.evaluationNo ?: (it.title ?: "") }) { e ->
                        EvaluationRow(e) { open(e.url) }
                        Hairline()
                    }
                }

                IntelView.PRICES -> {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text("PRICE HISTORY", style = Hud, color = BrandLit)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "What comparable work has actually been awarded for. " +
                                    "Built only from contracts published with an exact " +
                                    "figure — banded values are excluded, because a band " +
                                    "tells you nothing about a price.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted,
                            )
                            Spacer(Modifier.height(22.dp))
                        }
                    }
                    if (feed.priceHistory.isEmpty()) {
                        item {
                            Text(
                                "Not enough awards with exact values yet to show a range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkFaint,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                    items(feed.priceHistory, key = { it.term }) { band ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Text(band.term.uppercase(), style = Hud, color = InkMuted)
                            Spacer(Modifier.height(8.dp))
                            Text(formatPkr(band.median), style = Figure, color = Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "median of ${band.awards} awards  ·  " +
                                    "${formatPkr(band.low)} to ${formatPkr(band.high)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun HudLabel(text: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = Hud, color = BrandLit, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Hairline))
}

@Composable
private fun AwardRow(a: Award, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // The figure leads: it is the reason to look at this at all.
        Text(
            if (a.valuePkr != null) formatPkr(a.valuePkr) else (a.valueBand ?: "Value not published"),
            style = if (a.valuePkr != null) Figure else MaterialTheme.typography.bodyLarge,
            color = if (a.valuePkr != null) Ink else InkFaint,
        )
        Spacer(Modifier.height(8.dp))

        Text(
            a.title ?: "Not described",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))

        a.winner?.let {
            Row {
                Text("WON BY", style = Hud, color = InkFaint,
                     modifier = Modifier.width(84.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = SmdAccent,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
        }
        a.organisation?.let {
            Row {
                Text("BUYER", style = Hud, color = InkFaint, modifier = Modifier.width(84.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
        }
        a.awarded?.let {
            Row {
                Text("AWARDED", style = Hud, color = InkFaint, modifier = Modifier.width(84.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
        }
    }
}

@Composable
private fun EvaluationRow(e: Evaluation, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                e.bidCount?.toString() ?: "—",
                style = Figure,
                color = if ((e.bidCount ?: 0) > 0) Ink else InkFaint,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (e.bidCount == 1) "firm bid" else "firms bid",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            e.title ?: "Not described",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))

        if (e.lowest.isNotEmpty()) {
            Row {
                Text("LOWEST", style = Hud, color = InkFaint, modifier = Modifier.width(84.dp))
                Column {
                    e.lowest.take(3).forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = SmdAccent)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        e.organisation?.let {
            Row {
                Text("BUYER", style = Hud, color = InkFaint, modifier = Modifier.width(84.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

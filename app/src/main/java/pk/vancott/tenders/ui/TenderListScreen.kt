package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderNote
import pk.vancott.tenders.ui.theme.*

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TenderListScreen(
    vm: TenderViewModel,
    onOpen: (Tender) -> Unit,
    onFilters: () -> Unit,
    onMenu: () -> Unit,
) {
    val s by vm.state.collectAsState()
    val results by vm.results.collectAsState()

    // Grouped once per result set, off the drawing path.
    val groups = remember(results) {
        results.groupBy { bucketOf(it) }
            .toList()
            .sortedBy { it.first.ordinal }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        TopBar(
            title = "Tenders",
            subtitle = "${results.size} shown of ${s.feed?.count ?: 0}",
            onMenu = onMenu,
            action = {
                IconButton(onClick = vm::refresh, enabled = !s.loading) {
                    if (s.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Brand,
                                                  strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh", tint = InkMuted)
                    }
                }
            },
        )

        SearchBar(s.query, vm::setQuery)
        StageBar(s, vm)
        FilterBar(s, onFilters)

        s.error?.let { ErrorStrip(it, s.lastUpdated, s.feed != null) }

        if (s.loading && results.isEmpty() && s.feed == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else if (results.isEmpty()) {
            EmptyState(hasData = s.feed != null, scope = s.scope, onClear = vm::clearFilters)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
                item(key = "summary") { SummaryCard(s) }

                groups.forEach { (bucket, rows) ->
                    stickyHeader(key = "h_${bucket.name}") {
                        GroupHeader(bucket.label, rows.size)
                    }
                    itemsIndexed(
                        rows,
                        key = { _, t -> t.uid },
                        contentType = { _, _ -> "tender" },
                    ) { i, t ->
                        if (i > 0) RowDivider()
                        TenderRow(
                            t = t,
                            note = s.notes[t.uid] ?: TenderNote(),
                            onClick = { onOpen(t) },
                            onStar = { vm.toggleStar(t.uid) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The day at a glance.
 *
 * Opening straight into a list of thousands gives no sense of what needs doing.
 * These three numbers answer that before any scrolling.
 */
@Composable
private fun SummaryCard(s: UiState) {
    val all = s.feed?.tenders.orEmpty()
    val today = all.count { !it.isClosed && it.daysLeft == 0L }
    val week = all.count { !it.isClosed && (it.daysLeft ?: 99L) in 0L..7L }
    val smd = all.count { it.isSmd && !it.isClosed }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(Panel)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat(today.toString(), "closing today", if (today > 0) Critical else InkMuted)
        StatDivider()
        Stat(week.toString(), "this week", if (week > 0) Soon else InkMuted)
        StatDivider()
        Stat(smd.toString(), "open SMD", if (smd > 0) SmdAccent else InkMuted)
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Celias, fontSize = 27.sp, lineHeight = 30.sp, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(Hairline))
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = {
            Text("Search all tenders", style = MaterialTheme.typography.bodyMedium,
                 color = InkFaint)
        },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = InkMuted) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, "Clear", tint = InkMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = BrandLit,
        ),
    )
}

/**
 * Active / Pre-qualification / Expired.
 *
 * These come from what the portals publish, so switching here changes which
 * kind of notice you are looking at, not our opinion of it.
 */
@Composable
private fun StageBar(s: UiState, vm: TenderViewModel) {
    val all = s.feed?.tenders.orEmpty()
    val counts = remember(all) { all.groupingBy { stageOf(it) }.eachCount() }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stage.entries.forEach { stage ->
            val selected = s.stage == stage
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Brand.copy(alpha = 0.20f) else Panel)
                    .clickable { vm.setStage(stage) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stage.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) BrandLit else InkMuted,
                        maxLines = 1,
                    )
                    Text(
                        (counts[stage] ?: 0).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Ink else InkFaint,
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, accent: Color? = null, onClick: () -> Unit) {
    val tint = accent ?: BrandLit
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) tint.copy(alpha = 0.18f) else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) tint else InkMuted,
        )
    }
}

@Composable
private fun FilterBar(s: UiState, onFilters: () -> Unit) {
    val active = listOfNotNull(
        s.province, s.category, s.department, s.city, s.assignedTo, s.source,
    ).size + (if (s.withDocsOnly) 1 else 0)

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(if (active > 0) "Filters ($active)" else "Sort and filter", active > 0,
             onClick = onFilters)
        Spacer(Modifier.width(12.dp))
        Text(s.sortBy.label, style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
private fun ErrorStrip(msg: String, lastUpdated: Long, hasData: Boolean) {
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            if (lastUpdated > 0) "Could not refresh. Showing the last saved copy."
            else if (hasData) "Not connected yet. Showing the copy built into the app."
            else "Could not load tenders. $msg",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandLit,
        )
    }
}

@Composable
private fun EmptyState(hasData: Boolean, scope: Scope, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                !hasData -> "No tenders loaded yet"
                scope == Scope.STARRED -> "Nothing shortlisted yet"
                else -> "Nothing matches"
            },
            style = MaterialTheme.typography.titleMedium,
            color = InkMuted,
        )
        if (scope == Scope.STARRED && hasData) {
            Spacer(Modifier.height(8.dp))
            Text("Tap the star on any tender to add it here.",
                 style = MaterialTheme.typography.bodyMedium, color = InkFaint)
        } else if (hasData) {
            Spacer(Modifier.height(16.dp))
            Chip("Clear filters", true, onClick = onClear)
        }
    }
}

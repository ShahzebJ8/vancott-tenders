package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TenderListScreen(vm: TenderViewModel, onOpen: (Tender) -> Unit) {
    val s by vm.state.collectAsState()
    val all = s.feed?.tenders.orEmpty()
    val results = remember(all, s.query, s.scope, s.province, s.source, s.includeClosed) {
        applyFilters(all, s)
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        Header(s, results.size, onRefresh = vm::refresh)
        SearchBar(s.query, vm::setQuery)
        ScopeRow(s, all, vm)
        FilterRow(s, all, vm)

        s.error?.let { ErrorStrip(it, s.lastUpdated, all.isNotEmpty()) }

        if (s.loading && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Brand)
            }
        } else if (results.isEmpty()) {
            EmptyState(hasData = all.isNotEmpty(), onClear = vm::clearFilters)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(results, key = { _, t -> t.uid }) { i, t ->
                    if (i > 0) RowDivider()
                    TenderRow(t, onClick = { onOpen(t) })
                }
            }
        }
    }
}

@Composable
private fun Header(s: UiState, shown: Int, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("VANCOTT Tenders", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(shown).append(" shown")
                    s.feed?.let { append(" of ").append(it.count) }
                    if (s.lastUpdated > 0) {
                        append("  ·  updated ")
                        append(SimpleDateFormat("d MMM HH:mm", Locale.UK).format(Date(s.lastUpdated)))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
        }
        IconButton(onClick = onRefresh, enabled = !s.loading) {
            if (s.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Brand, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, "Refresh", tint = InkMuted)
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        placeholder = {
            Text(
                "Search all tenders",
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint,
            )
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
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = Brand,
        ),
    )
}

@Composable
private fun ScopeRow(s: UiState, all: List<Tender>, vm: TenderViewModel) {
    val smd = all.count { it.isSmd && !it.isClosed }
    val soon = all.count { !it.isClosed && (it.daysLeft ?: 99L) <= 7L }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("All", s.scope == Scope.ALL) { vm.setScope(Scope.ALL) }
        Chip("SMD ($smd)", s.scope == Scope.SMD) { vm.setScope(Scope.SMD) }
        Chip("Closing soon ($soon)", s.scope == Scope.CLOSING_SOON) { vm.setScope(Scope.CLOSING_SOON) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PanelRaised else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Ink else InkMuted,
        )
    }
}

@Composable
private fun FilterRow(s: UiState, all: List<Tender>, vm: TenderViewModel) {
    val provinces = remember(all) { all.mapNotNull { it.province }.distinct().sorted() }
    val sources = remember(all) { all.map { it.source to it.sourceName }.distinct() }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (s.province != null || s.source != null || s.includeClosed) {
            Chip("Clear", true) { vm.clearFilters() }
        }
        provinces.forEach { p ->
            Chip(p, s.province == p) { vm.setProvince(if (s.province == p) null else p) }
        }
        sources.forEach { entry ->
            Chip(entry.second, s.source == entry.first) {
                vm.setSource(if (s.source == entry.first) null else entry.first)
            }
        }
        Chip(if (s.includeClosed) "Hide closed" else "Show closed", s.includeClosed) {
            vm.toggleClosed()
        }
    }
}

@Composable
private fun ErrorStrip(msg: String, lastUpdated: Long, hasData: Boolean) {
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            // Always says what you ARE looking at, so old data is never
            // mistaken for current data.
            if (lastUpdated > 0) "Could not refresh. Showing the last saved copy."
            else if (hasData) "Not connected to the live feed yet. Showing the copy built into the app."
            else "Could not load tenders. $msg",
            style = MaterialTheme.typography.bodyMedium,
            color = Urgent,
        )
    }
}

@Composable
private fun EmptyState(hasData: Boolean, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasData) "Nothing matches" else "No tenders loaded yet",
            style = MaterialTheme.typography.titleMedium,
            color = InkMuted,
        )
        if (hasData) {
            Spacer(Modifier.height(16.dp))
            Chip("Clear filters", true, onClear)
        }
    }
}

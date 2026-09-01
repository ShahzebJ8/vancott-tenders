package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

/**
 * Sort and filter, all in one screen.
 *
 * Categories and departments are taken from the source websites verbatim - the
 * app never invents a grouping, so a filter here means exactly what it means on
 * PPRA. Departments are searchable because there are 700 of them; categories
 * are shown as a plain list because there are under 30.
 */
@Composable
fun FilterSheet(s: UiState, all: List<Tender>, vm: TenderViewModel, onClose: () -> Unit) {
    var deptQuery by remember { mutableStateOf("") }

    val categories = remember(all) {
        all.mapNotNull { it.category }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val departments = remember(all) {
        all.mapNotNull { it.organisation }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val provinces = remember(all) {
        all.mapNotNull { it.province }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    val sources = remember(all) { all.map { it.source to it.sourceName }.distinct() }
    val cities = remember(all) {
        all.mapNotNull { it.city }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }

    val shownDepts = remember(departments, deptQuery) {
        if (deptQuery.isBlank()) departments
        else departments.filter { it.first.contains(deptQuery, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort and filter", style = MaterialTheme.typography.titleLarge,
                 color = Ink, modifier = Modifier.weight(1f))
            Text(
                "Reset",
                style = MaterialTheme.typography.labelMedium,
                color = Brand,
                modifier = Modifier.clickable { vm.clearFilters(); deptQuery = "" }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Ink) }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {

            item { GroupTitle("Sort by") }
            items(SortBy.entries.toList()) { opt ->
                OptionRow(opt.label, null, s.sortBy == opt) { vm.setSortBy(opt) }
            }

            item { GroupTitle("Status") }
            item {
                OptionRow(
                    "Include closed tenders", null, s.includeClosed,
                ) { vm.toggleClosed() }
            }

            if (provinces.isNotEmpty()) {
                item { GroupTitle("Province") }
                item { OptionRow("All provinces", null, s.province == null) { vm.setProvince(null) } }
                items(provinces) { entry ->
                    OptionRow(entry.first, entry.second, s.province == entry.first) {
                        vm.setProvince(if (s.province == entry.first) null else entry.first)
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item { GroupTitle("Category") }
                item { OptionRow("All categories", null, s.category == null) { vm.setCategory(null) } }
                items(categories) { entry ->
                    OptionRow(entry.first, entry.second, s.category == entry.first) {
                        vm.setCategory(if (s.category == entry.first) null else entry.first)
                    }
                }
            }

            if (sources.size > 1) {
                item { GroupTitle("Source") }
                item { OptionRow("All sources", null, s.source == null) { vm.setSource(null) } }
                items(sources) { entry ->
                    OptionRow(entry.second, null, s.source == entry.first) {
                        vm.setSource(if (s.source == entry.first) null else entry.first)
                    }
                }
            }

            if (cities.isNotEmpty()) {
                item { GroupTitle("City (" + cities.size + ")") }
                item { OptionRow("All cities", null, s.city == null) { vm.setCity(null) } }
                items(cities) { entry ->
                    OptionRow(entry.first, entry.second, s.city == entry.first) {
                        vm.setCity(if (s.city == entry.first) null else entry.first)
                    }
                }
            }

            if (s.people.isNotEmpty()) {
                item { GroupTitle("Assigned to") }
                item {
                    OptionRow("Anyone", null, s.assignedTo == null) { vm.setAssignedTo(null) }
                }
                items(s.people) { person ->
                    OptionRow(person, null, s.assignedTo == person) {
                        vm.setAssignedTo(if (s.assignedTo == person) null else person)
                    }
                }
            }

            item { GroupTitle("Documents") }
            item {
                OptionRow(
                    "Only tenders with documents", null, s.withDocsOnly,
                ) { vm.toggleDocsOnly() }
            }

            item { GroupTitle("Department (" + departments.size + ")") }
            item {
                OutlinedTextField(
                    value = deptQuery,
                    onValueChange = { deptQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    placeholder = {
                        Text("Find a department", style = MaterialTheme.typography.bodyMedium,
                             color = InkFaint)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = InkMuted) },
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
            item {
                OptionRow("All departments", null, s.department == null) { vm.setDepartment(null) }
            }
            items(shownDepts) { entry ->
                OptionRow(entry.first, entry.second, s.department == entry.first) {
                    vm.setDepartment(if (s.department == entry.first) null else entry.first)
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(label: String) {
    Column {
        Spacer(Modifier.height(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Hairline))
    }
}

/** One choice. The count tells you how many tenders you would be left with. */
@Composable
private fun OptionRow(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (selected) Brand else Hairline)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Ink else InkMuted,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}

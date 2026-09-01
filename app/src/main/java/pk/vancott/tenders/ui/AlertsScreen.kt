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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.ui.theme.*

/**
 * Keyword alerts.
 *
 * The built-in SMD filter is our guess at what matters. This is where the user
 * says what actually matters to them - "video wall", "signage", "billboard
 * Lahore" - and gets told when one appears. Same matching rule as the search
 * bar, so what you save behaves exactly like what you typed.
 */
@Composable
fun AlertsScreen(vm: TenderViewModel, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    var newQuery by remember { mutableStateOf(s.query) }

    Column(Modifier.fillMaxSize().background(Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keyword alerts", style = MaterialTheme.typography.titleLarge,
                 color = Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Ink) }
        }

        Text(
            "You are told when a new tender matches any of these. Checked in the background, a few times a day.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newQuery,
                onValueChange = { newQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("e.g. video wall", style = MaterialTheme.typography.bodyMedium,
                         color = InkFaint)
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (newQuery.isBlank()) Panel else Brand)
                    .clickable(enabled = newQuery.isNotBlank()) {
                        vm.addSearch(newQuery.trim(), newQuery.trim())
                        newQuery = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("Add", style = MaterialTheme.typography.labelMedium,
                     color = if (newQuery.isBlank()) InkFaint else Ink)
            }
        }

        if (s.searches.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No alerts yet", style = MaterialTheme.typography.titleMedium, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a word above. Anything you would type into search works here.",
                    style = MaterialTheme.typography.bodyMedium, color = InkFaint,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                items(s.searches, key = { it.id }) { search ->
                    val count = vm.countFor(search)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(search.name, style = MaterialTheme.typography.titleMedium,
                                 color = Ink)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                // The live count proves the alert is sensible
                                // before you rely on it.
                                if (count == 1) "1 open tender matches now"
                                else "$count open tenders match now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (count > 0) SmdAccent else InkMuted,
                            )
                        }
                        Chip(
                            if (search.notify) "Notify on" else "Notify off",
                            search.notify,
                            SmdAccent,
                        ) { vm.toggleSearchNotify(search.id) }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = Critical,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable { vm.removeSearch(search.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    RowDivider()
                }
            }
        }
    }
}

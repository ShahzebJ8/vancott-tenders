package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.ui.theme.*

/** Colour is used only to show how much time is left. Nothing else is coloured. */
fun urgencyColor(t: Tender): Color = when {
    t.isClosed -> Expired
    t.daysLeft == null -> InkFaint
    t.daysLeft!! <= 2 -> Critical
    t.daysLeft!! <= 7 -> Urgent
    else -> InkMuted
}

fun countdownLabel(t: Tender): String = when {
    t.closing == null -> "—"
    t.isClosed -> "Closed"
    t.daysLeft == 0L -> "Today"
    t.daysLeft == 1L -> "1 day"
    else -> t.daysLeft.toString() + " days"
}

/**
 * One tender in the list.
 *
 * Deliberately plain: title, who it is for, and how long is left. Hierarchy
 * comes from type size and colour only - no cards, no gradients, no icons.
 */
@Composable
fun TenderRow(t: Tender, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (t.isSmd) {
                    Text(
                        "SMD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (t.isClosed) InkMuted else Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    listOfNotNull(t.organisation, t.city).joinToString(", ")
                        .ifBlank { "Organisation not stated" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                countdownLabel(t),
                style = MaterialTheme.typography.labelMedium,
                color = urgencyColor(t),
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

/** Hairline between rows - the only separator in the list. */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Hairline)
    )
}

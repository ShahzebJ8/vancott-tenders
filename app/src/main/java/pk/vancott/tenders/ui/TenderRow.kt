package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderNote
import pk.vancott.tenders.ui.theme.*

fun urgencyColor(t: Tender): Color = when {
    t.isClosed -> Expired
    t.daysLeft == null -> InkFaint
    t.daysLeft!! <= 2 -> Critical
    t.daysLeft!! <= 7 -> Soon
    else -> InkMuted
}

fun countdownLabel(t: Tender): String = when {
    t.closing == null -> "—"
    t.isClosed -> "Closed"
    t.daysLeft == 0L -> "Today"
    t.daysLeft == 1L -> "1 day"
    else -> t.daysLeft.toString() + " days"
}

/** Colour for a department block, derived from its own name so the same
 *  department always looks the same without keeping a list of them. */
private val BLOCK_COLORS = listOf(
    Color(0xFF3E6DA8), Color(0xFF3F8F7A), Color(0xFF8A6BAE),
    Color(0xFFA8703E), Color(0xFF4E7FA8), Color(0xFF7A8A3F),
)

fun blockColor(organisation: String?): Color =
    BLOCK_COLORS[((organisation ?: "").hashCode().let { if (it < 0) -it else it }) % BLOCK_COLORS.size]

/**
 * One tender in the list.
 *
 * Reads left to right the way you actually triage: who wants it, what it is,
 * how long is left. The days remaining is the largest thing on the row because
 * it is the one number that decides whether the rest matters.
 *
 * Everything shown is a value already computed and cached on the tender, so
 * scrolling does no work beyond drawing.
 */
@Composable
fun TenderRow(
    t: Tender,
    note: TenderNote,
    onClick: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Department block: a visual anchor so rows stop blurring together.
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(blockColor(t.organisation).copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsFor(t.organisation),
                style = MaterialTheme.typography.labelSmall,
                color = blockColor(t.organisation),
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            if (t.isSmd || note.starred || note.assignedTo.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.isSmd) Tag("SMD", SmdAccent)
                    if (note.starred) Tag("Shortlist", StarAccent)
                    if (note.assignedTo.isNotBlank()) Tag(note.assignedTo, PersonAccent)
                }
                Spacer(Modifier.height(6.dp))
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

        Spacer(Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(72.dp),
        ) {
            val days = t.daysLeft
            if (days != null && !t.isClosed && days > 1) {
                // Big number, small unit. Readable without being read.
                Text(
                    days.toString(),
                    fontFamily = Celias,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    color = urgencyColor(t),
                )
                Text(
                    "days left",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    countdownLabel(t),
                    style = MaterialTheme.typography.labelMedium,
                    color = urgencyColor(t),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                if (note.starred) "★" else "☆",
                fontFamily = Celias,
                fontSize = 20.sp,
                color = if (note.starred) StarAccent else InkFaint,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onStar)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun Tag(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Sticky group heading in the list. */
@Composable
fun GroupHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(Void).padding(start = 16.dp, end = 20.dp,
                                                         top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = BrandLit,
             modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 71.dp, end = 20.dp).height(1.dp)
            .background(Hairline)
    )
}

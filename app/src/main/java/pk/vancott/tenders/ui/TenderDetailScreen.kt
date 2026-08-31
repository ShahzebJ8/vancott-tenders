package pk.vancott.tenders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.ui.theme.*

/**
 * Everything needed to actually bid, in the order you need it:
 * how long you have, what it is, where it goes, then the documents.
 */
@Composable
fun TenderDetailScreen(t: Tender, onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
            }
            Text(
                t.sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
        }

        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            // --- deadline first: it decides whether anything else matters ---
            CountdownBanner(t)

            Spacer(Modifier.height(18.dp))

            if (t.isSmd) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Brand)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("SMD / LED", style = MaterialTheme.typography.labelSmall, color = Ink)
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(t.title, style = MaterialTheme.typography.titleLarge, color = Ink)

            t.description?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
            }

            // A corrigendum can move a deadline. Loud, because missing one
            // costs the bid.
            t.tags.filter { it.startsWith("corrigendum") }.forEach { note ->
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Urgent.copy(alpha = 0.15f)).padding(12.dp)
                ) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = Urgent)
                }
            }

            Section("Tender")
            Field("Tender number", t.tenderNo)
            Field("Reference", t.detail["reference_no"])
            Field("Type", t.detail["tender_type"])
            Field("Category", t.category)
            Field("Procurement category", t.detail["procurement_category"])
            Field("Procedure", t.detail["procurement_procedure"])
            Field("Method", t.detail["method"])
            Field("Nature", t.detail["tender_nature"])
            Field("Published", t.published)
            Field("Closing", t.closing)
            Field("Opening time", t.detail["opening_time"])
            Field("Bid security", t.value)
            Field("Bid validity", t.detail["bid_validity"])

            Section("Where the bid goes")
            Field("Organisation", t.organisation)
            Field("Office", t.detail["office_name"])
            Field("Address", t.detail["office_address"])
            Field("City", t.city)
            Field("Province", t.province)
            Field("Contact", t.detail["contact_person"])
            Field("Email", t.detail["contact_email"])
            Field("Phone", t.detail["contact_phone"])

            if (t.docUrls.isNotEmpty()) {
                Section("Documents")
                t.docUrls.forEachIndexed { i, url ->
                    ActionButton(
                        if (t.docUrls.size == 1) "Open tender document"
                        else "Open document " + (i + 1),
                        primary = false,
                    ) { open(url) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Section("Source")
            ActionButton("Open on " + t.sourceName, primary = true) { open(t.url) }

            if (t.matchedTerms.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                // Shows WHY this was flagged, so the classifier is auditable
                // rather than a black box you have to trust.
                Text(
                    "Flagged on: " + t.matchedTerms.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
        }
    }
}

@Composable
private fun CountdownBanner(t: Tender) {
    val c = urgencyColor(t)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(countdownLabel(t), style = MaterialTheme.typography.titleLarge, color = c)
        Spacer(Modifier.width(14.dp))
        Text(
            when {
                t.closing == null -> "No closing date published"
                t.isClosed -> "Closed on " + t.closing
                else -> "Closes " + t.closing
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
    }
}

@Composable
private fun Section(label: String) {
    Spacer(Modifier.height(26.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(InkFaint.copy(alpha = 0.4f)))
    Spacer(Modifier.height(10.dp))
}

/**
 * A field the source did not publish is skipped entirely rather than shown
 * blank or filled with a plausible guess.
 */
@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier.width(122.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(if (primary) Brand else PanelRaised)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

package pk.vancott.tenders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pk.vancott.tenders.data.DocumentDownloader
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderNote
import pk.vancott.tenders.ui.theme.*

/**
 * Everything needed to act on a tender, in the order you need it:
 * how long you have, what it is, who wants it, where the bid goes, documents.
 */
@Composable
fun TenderDetailScreen(t: Tender, vm: TenderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.state.collectAsState()
    val note = s.notes[t.uid] ?: TenderNote()

    var noteText by remember(t.uid) { mutableStateOf(note.note) }
    var personText by remember(t.uid) { mutableStateOf(note.assignedTo) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, t.title)
            putExtra(Intent.EXTRA_TEXT, buildShareText(t))
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Send tender to")) }
    }

    fun downloadSet() {
        val n = DocumentDownloader.downloadAll(context, t)
        DocumentDownloader.toast(
            context,
            when (n) {
                0 -> "This tender has no documents published on the portal"
                1 -> "Downloading 1 document to Downloads"
                else -> "Downloading $n documents to Downloads"
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
            }
            Text(t.sourceName, style = MaterialTheme.typography.labelSmall,
                 color = InkMuted, modifier = Modifier.weight(1f))
            Text(
                if (note.starred) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (note.starred) StarAccent else InkFaint,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { vm.toggleStar(t.uid) }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 44.dp)
        ) {
            CountdownBanner(t)

            Spacer(Modifier.height(16.dp))

            if (t.isSmd) {
                Tag("SMD / LED match", SmdAccent)
                Spacer(Modifier.height(10.dp))
            }

            Text(t.title, style = MaterialTheme.typography.titleLarge, color = Ink)

            t.description?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
            }

            // A corrigendum can move a deadline. Shown loudly, because missing
            // one costs the bid.
            t.tags.filter { it.startsWith("corrigendum") }.forEach { warning ->
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Critical.copy(alpha = 0.14f)).padding(12.dp)
                ) {
                    Text(warning, style = MaterialTheme.typography.bodyMedium, color = Critical)
                }
            }

            Section("Department and location")
            Field("Department", t.organisation)
            Field("Office", t.detail["office_name"])
            Field("Address", t.detail["office_address"])
            Field("City", t.city)
            Field("Province", t.province)
            Field("Listed on", t.sourceName)

            Section("Who to contact")
            Field("Contact person", t.detail["contact_person"])
            Field("Email", t.detail["contact_email"])
            Field("Phone", t.detail["contact_phone"])
            if (t.detail["contact_person"] == null && t.detail["contact_email"] == null) {
                NotStated("The portal did not publish contact details for this tender.")
            }

            Section("What is required")
            Field("Category", t.category)
            Field("Procurement category", t.detail["procurement_category"])
            Field("Tender type", t.detail["tender_type"])
            Field("Procedure", t.detail["procurement_procedure"])
            Field("Method", t.detail["method"])
            Field("Nature", t.detail["tender_nature"])

            Section("Money and dates")
            Field("Tender number", t.tenderNo)
            Field("Reference", t.detail["reference_no"])
            Field("Published", t.published)
            Field("Closing", t.closing)
            Field("Opening time", t.detail["opening_time"])
            Field("Bid security", t.value)
            Field("Bid validity", t.detail["bid_validity"])
            if (t.value == null) {
                NotStated("Bid security was not on the portal listing. It is normally stated inside the tender document.")
            }

            Section("Your notes")
            OutlinedTextField(
                value = personText,
                onValueChange = { personText = it; vm.assign(t.uid, it.trim()) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Assigned to", style = MaterialTheme.typography.labelSmall) },
                placeholder = {
                    Text("Who is handling this", style = MaterialTheme.typography.bodyMedium,
                         color = InkFaint)
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = detailFieldColors(),
            )
            if (s.people.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.people.forEach { p ->
                        Chip(p, personText == p, PersonAccent) {
                            personText = p; vm.assign(t.uid, p)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it; vm.setNote(t.uid, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note", style = MaterialTheme.typography.labelSmall) },
                placeholder = {
                    Text("e.g. called them, waiting on drawings",
                         style = MaterialTheme.typography.bodyMedium, color = InkFaint)
                },
                minLines = 3,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = detailFieldColors(),
            )

            Spacer(Modifier.height(14.dp))
            Text("Remind me before it closes", style = MaterialTheme.typography.labelSmall,
                 color = InkMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 3, 7, 14).forEach { d ->
                    Chip(
                        if (d == 0) "Off" else "$d days",
                        note.remindDaysBefore == d,
                        StarAccent,
                    ) { vm.setRemind(t.uid, d) }
                }
            }

            Section("Documents")
            if (t.docUrls.isEmpty()) {
                NotStated("No documents published on the portal for this tender.")
            } else {
                ActionButton(
                    if (t.docUrls.size == 1) "Download document set (1 file)"
                    else "Download document set (" + t.docUrls.size + " files)",
                    primary = true,
                ) { downloadSet() }
                Spacer(Modifier.height(8.dp))
                t.docUrls.forEachIndexed { i, url ->
                    ActionButton(
                        if (t.docUrls.size == 1) "Open in browser" else "Open document " + (i + 1),
                        primary = false,
                    ) { open(url) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Section("Send and open")
            ActionButton("Send to someone", primary = true) { share() }
            Spacer(Modifier.height(8.dp))
            ActionButton("Open on " + t.sourceName, primary = false) { open(t.url) }

            if (t.matchedTerms.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                // Shows WHY this was flagged, so the match is auditable rather
                // than something you have to take on trust.
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
private fun detailFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    unfocusedBorderColor = Hairline,
    focusedContainerColor = Panel,
    unfocusedContainerColor = Panel,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedLabelColor = BrandLit,
    unfocusedLabelColor = InkMuted,
    cursorColor = BrandLit,
)

@Composable
private fun CountdownBanner(t: Tender) {
    val c = urgencyColor(t)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.copy(alpha = 0.13f)).padding(horizontal = 16.dp, vertical = 14.dp),
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
    Text(label, style = MaterialTheme.typography.labelSmall, color = BrandLit)
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted,
             modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

/** Says plainly that something is missing from the source, rather than leaving
 *  an empty space the reader has to interpret. */
@Composable
private fun NotStated(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = InkFaint,
         modifier = Modifier.padding(vertical = 4.dp))
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

/**
 * The message an employee receives.
 *
 * Written so it stands alone in WhatsApp: what it is, when it closes, and
 * exactly where the bid goes - so nobody has to open the app to act on it.
 */
private fun buildShareText(t: Tender): String = buildString {
    appendLine(t.title)
    appendLine()
    t.tenderNo?.let { appendLine("Tender no: $it") }
    t.organisation?.let { appendLine("Department: $it") }
    t.city?.let { appendLine("City: $it") }
    t.closing?.let { appendLine("Closes: $it  (" + countdownLabel(t) + ")") }
    t.value?.let { appendLine("Bid security: $it") }
    t.detail["office_address"]?.let { appendLine("Submit at: $it") }
    t.detail["contact_person"]?.let { appendLine("Contact: $it") }
    t.detail["contact_email"]?.let { appendLine("Email: $it") }
    appendLine()
    appendLine(t.url)
    if (t.docUrls.isNotEmpty()) {
        appendLine()
        appendLine("Documents:")
        t.docUrls.forEach { appendLine(it) }
    }
    appendLine()
    append("Sent from VANCOTT Tender Desk")
}

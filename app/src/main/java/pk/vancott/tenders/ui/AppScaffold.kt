package pk.vancott.tenders.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.ui.theme.*

/**
 * The slide-out menu.
 *
 * Deliberately simple: it slides in from the left, dims what is behind it, and
 * tapping outside closes it. No gesture to learn, which matters when the people
 * using this are not app enthusiasts.
 */
@Composable
fun DrawerScrim(open: Boolean, onClose: () -> Unit) {
    val alpha by animateFloatAsState(if (open) 0.55f else 0f, tween(180), label = "scrim")
    if (alpha > 0.01f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
        )
    }
}

@Composable
fun DrawerMenu(
    current: Section,
    counts: Map<Section, Int>,
    onPick: (Section) -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(288.dp)
            .background(Panel)
            .padding(top = 34.dp)
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
            Text(
                "VANCOTT",
                fontFamily = Celias,
                fontWeight = FontWeight.Light,
                fontSize = 20.sp,
                letterSpacing = 4.sp,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text("Tender Desk", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(8.dp))

        Section.entries.forEach { section ->
            val selected = section == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(section) }
                    .background(if (selected) PanelRaised else Color.Transparent)
                    .padding(horizontal = 22.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp).height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Brand else Color.Transparent)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    section.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) Ink else InkMuted,
                    modifier = Modifier.weight(1f),
                )
                counts[section]?.takeIf { it > 0 }?.let {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) BrandLit else InkFaint,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Data from PPRA, EPADS and Punjab PPRA.\nNothing here replaces the official documents.",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.padding(22.dp),
        )
    }
}

/** The top bar shared by every section: menu button, title, optional action. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onMenu: () -> Unit,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drawn rather than an icon font, so it stays crisp and needs no asset.
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMenu)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Box(Modifier.width(19.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                    .background(Ink))
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = InkMuted)
            }
        }
        action?.invoke()
    }
}

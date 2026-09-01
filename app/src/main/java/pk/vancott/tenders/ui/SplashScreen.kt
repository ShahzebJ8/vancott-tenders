package pk.vancott.tenders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.ui.theme.Brand
import pk.vancott.tenders.ui.theme.Celias
import pk.vancott.tenders.ui.theme.Ink
import pk.vancott.tenders.ui.theme.InkMuted
import pk.vancott.tenders.ui.theme.Void

/**
 * Shown while the tender file is read in the background.
 *
 * It is a real screen, not decoration: reading 3 MB of tenders takes a moment
 * on an older phone, and showing the brand beats showing a frozen empty list.
 */
@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().background(Void),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "VANCOTT",
            fontFamily = Celias,
            fontWeight = FontWeight.Light,
            fontSize = 34.sp,
            letterSpacing = 6.sp,
            color = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(46.dp).height(2.dp).background(Brand))
        Spacer(Modifier.height(10.dp))
        Text(
            "Tender Desk",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
        )
    }
}

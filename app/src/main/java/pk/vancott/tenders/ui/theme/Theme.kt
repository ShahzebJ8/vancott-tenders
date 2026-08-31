package pk.vancott.tenders.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Deliberately dark in both system modes. This is a field tool used on site and
// in vehicles; a white screen at night is worse than useless, and the LED-board
// identity only works on a black ground.
private val Scheme = darkColorScheme(
    primary = Brand,
    onPrimary = Ink,
    secondary = Brand,
    background = Void,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = InkMuted,
    outline = InkFaint,
    error = Critical,
)

@Composable
fun VancottTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}

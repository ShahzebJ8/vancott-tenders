package pk.vancott.tenders.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.R

/**
 * Celias, the VANCOTT brand face. One family for the whole app.
 *
 * Three weights are available (Thin, Light, Regular) and hierarchy is built
 * from size and colour rather than from extra typefaces, so the app stays
 * quiet and consistent.
 */
val Celias = FontFamily(
    Font(R.font.celias_thin, FontWeight.Thin),
    Font(R.font.celias_light, FontWeight.Light),
    Font(R.font.celias_regular, FontWeight.Normal),
)

val AppTypography = Typography(
    // Screen title
    titleLarge = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    // Tender title in a row
    titleMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    // Organisation, description, secondary lines
    bodyMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Small labels: section headings, chips, field names.
    labelSmall = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp,
    ),
)

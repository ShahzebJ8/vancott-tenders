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
 * Sizes are set for readability first. Nothing is below 14sp, because the
 * smallest text was too small to read comfortably on a small phone - and the
 * people using this are reading deadlines and money, where squinting is a real
 * cost. All sizes are in sp, so Android's own "larger text" setting scales them
 * further for anyone who needs it.
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
        fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.2).sp,
    ),
    // Tender title in a row
    titleMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 25.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 17.sp, lineHeight = 25.sp,
    ),
    // Organisation, description, secondary lines
    bodyMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    // Small labels: section headings, chips, field names. This is the floor.
    labelSmall = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
)

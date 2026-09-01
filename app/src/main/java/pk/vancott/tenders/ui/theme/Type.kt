package pk.vancott.tenders.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import pk.vancott.tenders.R

/**
 * Celias, carried over from the VANCOTT site.
 *
 * The site's system is the reference: Light weight everywhere, display text
 * uppercase with slightly negative tracking and tight leading, and a "HUD"
 * label - very small, uppercase, widely tracked - used as the marker above a
 * block. The contrast between that tiny tracked label and a large light
 * heading is what the identity actually rests on, so it is reproduced here
 * rather than approximated.
 *
 * The one departure from the site: body text is set larger than a website
 * would use it. This is read at arm's length, outdoors, often by someone who
 * needs it bigger, so nothing drops below 14sp. All sizes are in sp and scale
 * again with Android's own text-size setting.
 */
val Celias = FontFamily(
    Font(R.font.celias_thin, FontWeight.Thin),
    Font(R.font.celias_light, FontWeight.Light),
    Font(R.font.celias_regular, FontWeight.Normal),
)

/** The site's `.hud`: the marker above a section. Uppercase, widely tracked. */
val Hud = TextStyle(
    fontFamily = Celias,
    fontWeight = FontWeight.Light,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 2.6.sp,          // the site's 0.32em, at this size
)

/** Large figures: contract values, day counts, totals. */
val Figure = TextStyle(
    fontFamily = Celias,
    fontWeight = FontWeight.Light,
    fontSize = 30.sp,
    lineHeight = 33.sp,
    letterSpacing = (-0.5).sp,
    textAlign = TextAlign.Start,
)

val FigureSmall = TextStyle(
    fontFamily = Celias,
    fontWeight = FontWeight.Light,
    fontSize = 22.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.3).sp,
)

val AppTypography = Typography(
    // Screen title: uppercase, light, tight - the site's .h-lg, scaled down.
    titleLarge = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.3).sp,
    ),
    // A tender or story headline in a list.
    titleMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 17.sp, lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    // Field names, chips, counts. The floor for readability.
    labelSmall = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Light,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Celias, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
)

package pk.vancott.tenders.ui.theme

import androidx.compose.ui.graphics.Color

// Dark ground with a cool blue cast rather than neutral grey, so the surfaces
// feel like part of the brand instead of an absence of colour.
val Void        = Color(0xFF0A0D14)   // page background
val Panel       = Color(0xFF121722)   // inputs, chips
val PanelRaised = Color(0xFF1B2230)   // selected chip, buttons
val Hairline    = Color(0xFF232B3A)   // separators

// Brand blue and a lighter tint of it for small accents on dark.
val Brand       = Color(0xFF2769B3)
val BrandLit    = Color(0xFF6BA8E0)

// Accents. Each one means exactly one thing, so colour stays informative.
val SmdAccent   = Color(0xFF3FBFA0)   // an SMD / LED match
val StarAccent  = Color(0xFFE0B341)   // shortlisted by you
val PersonAccent= Color(0xFF9B87D8)   // assigned to someone

val Ink         = Color(0xFFF0F3F8)   // primary text
val InkMuted    = Color(0xFF98A2B3)   // secondary text
val InkFaint    = Color(0xFF5F6B7E)   // faint text

// Deadline pressure.
val Critical    = Color(0xFFE0574B)   // closes within 48 hours
val Soon        = Color(0xFFE0964B)   // closes within a week
val Expired     = Color(0xFF5F6B7E)   // already closed

package pk.vancott.tenders.ui.theme

import androidx.compose.ui.graphics.Color

// A small, quiet palette. Dark ground, one brand blue, and three deadline
// colours. Nothing else is coloured, so colour always means something.
val Void        = Color(0xFF0B0B0F)   // page background
val Panel       = Color(0xFF14141A)   // input / chip background
val PanelRaised = Color(0xFF1E1E26)   // selected chip
val Hairline    = Color(0xFF23232C)   // row separators

val Brand       = Color(0xFF2769B3)   // VANCOTT blue

val Ink         = Color(0xFFF2F3F5)   // primary text
val InkMuted    = Color(0xFF9096A1)   // secondary text
val InkFaint    = Color(0xFF5A606B)   // faint text

// Deadline states only.
val Urgent      = Color(0xFFD9963E)   // closes within a week
val Critical    = Color(0xFFD1584C)   // closes within 48 hours
val Expired     = Color(0xFF5A606B)   // already closed

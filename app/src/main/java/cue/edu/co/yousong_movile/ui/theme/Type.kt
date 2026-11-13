package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cue.edu.co.yousong_movile.R

/**
 * Manrope (SIL OFL 1.1, bundled; license in assets/licenses). Static 400/500/700/800 instances
 * were cut from the official google/fonts variable file so minSdk 24 renders real weights.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

private val Tight = (-0.02).em

private fun style(size: Int, line: Int, weight: FontWeight, tracking: androidx.compose.ui.unit.TextUnit = 0.sp) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking)

// Scale (sp): 12 / 14 / 16 / 20 / 24 / 32.
val Typography = Typography(
    displayLarge = style(32, 38, FontWeight.ExtraBold, Tight),
    displayMedium = style(32, 38, FontWeight.Bold, Tight),
    displaySmall = style(24, 30, FontWeight.ExtraBold, Tight),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, Tight),
    headlineMedium = style(24, 30, FontWeight.ExtraBold, Tight),
    headlineSmall = style(20, 26, FontWeight.ExtraBold, Tight),
    titleLarge = style(20, 26, FontWeight.Bold, Tight),
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.Bold),
    labelMedium = style(12, 16, FontWeight.Bold),
    labelSmall = style(12, 16, FontWeight.Medium),
)

/** Tabular figures so votes, waits and durations do not jitter as they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The queue position numeral: the one oversized typographic element in the app. */
val PositionNumeral = style(24, 28, FontWeight.ExtraBold, Tight).tabular()

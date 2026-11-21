package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radii follow hierarchy, growing with the size of the thing: list-row covers and inputs are tight (8),
 * genre tiles and cards are 12-16, carousel covers 16, the now-playing hero 24, sheets 28.
 * Anything you press to vote or filter is a full pill (CircleShape at the call site).
 */
val YouSongShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

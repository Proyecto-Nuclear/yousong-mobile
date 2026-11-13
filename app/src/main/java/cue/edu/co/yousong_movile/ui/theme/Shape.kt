package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radii follow hierarchy: covers and inputs are tight (8), cards and sheets are soft (14),
 * and anything you press to vote or filter is a full pill (CircleShape at the call site).
 */
val YouSongShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

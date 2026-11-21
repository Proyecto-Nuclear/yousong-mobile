package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * "Noche de verbena": the violet night of a Colombian street party, lit by mango paper lanterns,
 * with one aqua glow reserved for what is happening now (the song playing, the head of the queue,
 * focus). Dark is the first-class look. Planes are separated by container steps, never shadows.
 * Every Material 3 role is defined so no baseline purple can leak through.
 * Contrast ratios for every pair are listed in the README.
 */

// Dark tokens
private val Night = Color(0xFF14101F)
private val NightLowest = Color(0xFF0F0C18)
private val NightLow = Color(0xFF19142A)
private val NightSurface = Color(0xFF1E1830)
private val NightHigh = Color(0xFF241D3A)
private val NightRaised = Color(0xFF2A2243)
private val NightHairline = Color(0xFF342B52)
private val NightOutline = Color(0xFF8A7FB0)
private val Moon = Color(0xFFF6F1FF)
private val MoonMuted = Color(0xFFBDB4D6)
private val Mango = Color(0xFFFFB547)
private val OnMango = Color(0xFF1B1530)
private val Aqua = Color(0xFF3DD6C6)
private val Lilac = Color(0xFFC9B8FF)

// Light tokens
private val Day = Color(0xFFF6F3FB)
private val DaySurface = Color(0xFFFFFFFF)
private val DayContainer = Color(0xFFECE6F6)
private val Ink = Color(0xFF1B1530)
private val InkMuted = Color(0xFF50476B)
private val Violet = Color(0xFF5B3FD0)
private val DayOutline = Color(0xFF7D7399)
// The brief's #0B7F73 reaches only 4.45:1 on the light background; one step darker passes (5.0:1).
private val DayLive = Color(0xFF09766B)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Mango,
    onPrimary = OnMango,
    primaryContainer = Color(0xFF4D3510),
    onPrimaryContainer = Color(0xFFFFE2B8),
    inversePrimary = Color(0xFF8A5A00),
    secondary = Lilac,
    onSecondary = Color(0xFF2A1C5E),
    secondaryContainer = Color(0xFF3B3163),
    onSecondaryContainer = Color(0xFFE9E1FF),
    tertiary = Aqua,
    onTertiary = Color(0xFF0B2B28),
    tertiaryContainer = Color(0xFF0F3D3A),
    onTertiaryContainer = Color(0xFFB6F5EC),
    background = Night,
    onBackground = Moon,
    surface = NightSurface,
    onSurface = Moon,
    surfaceVariant = NightRaised,
    onSurfaceVariant = MoonMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Moon,
    inverseOnSurface = NightSurface,
    error = Color(0xFFFF8F8F),
    onError = Color(0xFF3A0A10),
    errorContainer = Color(0xFF3A1F2B),
    onErrorContainer = Color(0xFFFFD9DC),
    outline = NightOutline,
    outlineVariant = NightHairline,
    scrim = Color(0xFF07050D),
    surfaceBright = NightHairline,
    surfaceDim = Night,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightRaised,
)

/*
 * Light keeps mango as the call-to-action fill ([YouSongExtras.cta]), but Material uses `primary`
 * as a foreground color too (text buttons, progress, cursor, checkboxes), where mango on white is
 * 1.6:1. So in light `primary` is the accent-text violet and mango lives in primaryContainer.
 */
internal val LightColors: ColorScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Mango,
    onPrimaryContainer = OnMango,
    inversePrimary = Mango,
    secondary = Violet,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DCFF),
    onSecondaryContainer = Color(0xFF1F1248),
    tertiary = DayLive,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F2EC),
    onTertiaryContainer = Color(0xFF0B3D37),
    background = Day,
    onBackground = Ink,
    surface = DaySurface,
    onSurface = Ink,
    surfaceVariant = DayContainer,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2B2442),
    inverseOnSurface = Moon,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADCE0),
    onErrorContainer = Color(0xFF410E0B),
    outline = DayOutline,
    outlineVariant = Color(0xFFDCD5EA),
    scrim = Color(0xFF07050D),
    surfaceBright = DaySurface,
    surfaceDim = Color(0xFFE2DCEE),
    surfaceContainerLowest = DaySurface,
    surfaceContainerLow = Color(0xFFF9F7FD),
    surfaceContainer = Color(0xFFF1ECF9),
    surfaceContainerHigh = DayContainer,
    surfaceContainerHighest = Color(0xFFE4DDF1),
)

/** Roles Material 3 does not name: the live accent, the mango CTA, success and decorative hairlines. */
@Immutable
data class YouSongExtras(
    val live: Color,
    val cta: Color,
    val onCta: Color,
    val success: Color,
    val onSuccess: Color,
    val hairline: Color,
    val coverScrim: Color,
    /** Ink for anything drawn on a [GenreTileColors] tile (all tiles are light enough for it). */
    val onTile: Color,
)

internal val DarkExtras = YouSongExtras(
    live = Aqua,
    cta = Mango,
    onCta = OnMango,
    success = Color(0xFF17332F),
    onSuccess = Color(0xFFCFF7EF),
    hairline = NightHairline,
    coverScrim = Color(0xCC14101F),
    onTile = OnMango,
)

internal val LightExtras = YouSongExtras(
    live = DayLive,
    cta = Mango,
    onCta = OnMango,
    success = Color(0xFFD3F2EC),
    onSuccess = Color(0xFF0B3D37),
    hairline = Color(0xFFDCD5EA),
    coverScrim = Color(0xCC1B1530),
    onTile = OnMango,
)

/** Festival tiles for "Explorar"; each carries #1B1530 text at 8.2:1 or better. */
val GenreTileColors = listOf(
    Mango, Aqua, Color(0xFFFF8FB8), Color(0xFFB9A6FF), Color(0xFFC6E86B), Color(0xFFFF9E7A),
    Color(0xFF7CC8FF), Color(0xFFF2D49B),
)

val LocalYouSongExtras = staticCompositionLocalOf { DarkExtras }

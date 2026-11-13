package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * YouSong palette. Dark is the first-class look from the team prototype: a night-bar
 * ground, sage for actions, and one cool "live" blue reserved for what is happening now
 * (the head of the queue, focus). Planes are separated by container steps, never shadows.
 * Every Material 3 role is defined so no baseline purple can leak through.
 */

// Dark tokens
private val NightGround = Color(0xFF0E0F0E)
private val NightSurface = Color(0xFF171917)
private val NightRaised = Color(0xFF222522)
private val NightOutline = Color(0xFF6B716A)
private val NightHairline = Color(0xFF2A2E29)
private val Bone = Color(0xFFF2F4EE)
private val BoneMuted = Color(0xFFB3B8AD)
private val Sage = Color(0xFFCBDDB0)
private val OnSage = Color(0xFF172012)
private val LiveBlue = Color(0xFFB8D8FF)

// Light tokens
private val DayGround = Color(0xFFF1F4F1)
private val DaySurface = Color(0xFFFFFFFF)
private val DayContainer = Color(0xFFE7ECE7)
private val Ink = Color(0xFF161916)
private val InkMuted = Color(0xFF4A5048)
private val Forest = Color(0xFF2F4A2C)
private val OnForest = Color(0xFFF2F6EE)
private val DayOutline = Color(0xFF7A8177)
private val DayLive = Color(0xFF1F5FBF)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Sage,
    onPrimary = OnSage,
    primaryContainer = Color(0xFF34432C),
    onPrimaryContainer = Color(0xFFDDEBC8),
    inversePrimary = Color(0xFF4A6440),
    secondary = Color(0xFFC2C9B8),
    onSecondary = Color(0xFF1E231B),
    secondaryContainer = Color(0xFF2E342C),
    onSecondaryContainer = Color(0xFFE1E8D6),
    tertiary = LiveBlue,
    onTertiary = Color(0xFF0A2540),
    tertiaryContainer = Color(0xFF1C3550),
    onTertiaryContainer = Color(0xFFD6E7FF),
    background = NightGround,
    onBackground = Bone,
    surface = NightSurface,
    onSurface = Bone,
    surfaceVariant = NightRaised,
    onSurfaceVariant = BoneMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Bone,
    inverseOnSurface = Color(0xFF1B1D1B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B2323),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = NightOutline,
    outlineVariant = NightHairline,
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2E29),
    surfaceDim = NightGround,
    surfaceContainerLowest = Color(0xFF0A0B0A),
    surfaceContainerLow = Color(0xFF131513),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF1C1F1C),
    surfaceContainerHighest = NightRaised,
)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = OnForest,
    primaryContainer = Color(0xFFD3E3C6),
    onPrimaryContainer = Color(0xFF172012),
    inversePrimary = Sage,
    secondary = Color(0xFF56604F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3D4),
    onSecondaryContainer = Color(0xFF1A2117),
    tertiary = DayLive,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E4FA),
    onTertiaryContainer = Color(0xFF0B2A5A),
    background = DayGround,
    onBackground = Ink,
    surface = DaySurface,
    onSurface = Ink,
    surfaceVariant = DayContainer,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2C312C),
    inverseOnSurface = Color(0xFFEEF2EC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = DayOutline,
    outlineVariant = Color(0xFFD3D9D1),
    scrim = Color(0xFF000000),
    surfaceBright = DaySurface,
    surfaceDim = Color(0xFFDCE1DB),
    surfaceContainerLowest = DaySurface,
    surfaceContainerLow = Color(0xFFF7F9F6),
    surfaceContainer = Color(0xFFEEF2EE),
    surfaceContainerHigh = DayContainer,
    surfaceContainerHighest = Color(0xFFDFE5DF),
)

/** Roles Material 3 does not name: the live accent, success feedback and decorative hairlines. */
@Immutable
data class YouSongExtras(
    val live: Color,
    val success: Color,
    val onSuccess: Color,
    val hairline: Color,
    val coverScrim: Color,
)

internal val DarkExtras = YouSongExtras(
    live = LiveBlue,
    success = Color(0xFF202D26),
    onSuccess = Color(0xFFDAFFE6),
    hairline = NightHairline,
    coverScrim = Color(0xCC0E0F0E),
)

internal val LightExtras = YouSongExtras(
    live = DayLive,
    success = Color(0xFFD9F2E1),
    onSuccess = Color(0xFF0F3A20),
    hairline = Color(0xFFD9DED8),
    coverScrim = Color(0xCC161916),
)

val LocalYouSongExtras = staticCompositionLocalOf { DarkExtras }

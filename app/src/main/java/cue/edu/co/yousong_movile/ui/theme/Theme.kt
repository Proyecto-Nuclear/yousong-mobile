package cue.edu.co.yousong_movile.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when the system "remove animations" setting is on; ambient motion must stop. */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun systemReducesMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

@Composable
fun YousongmovileTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { systemReducesMotion(context) }
    CompositionLocalProvider(
        LocalYouSongExtras provides if (darkTheme) DarkExtras else LightExtras,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = YouSongShapes,
            content = content,
        )
    }
}

object YouSongTheme {
    val extras: YouSongExtras @Composable get() = LocalYouSongExtras.current
}

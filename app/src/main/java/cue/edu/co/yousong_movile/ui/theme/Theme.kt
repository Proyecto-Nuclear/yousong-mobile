package cue.edu.co.yousong_movile.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when the system "remove animations" setting is on; ambient motion must stop. */
val LocalReduceMotion = staticCompositionLocalOf { false }

internal fun systemReducesMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/** Follows the system "remove animations" switch live, not just at first composition. */
@Composable
private fun rememberSystemReducesMotion(context: Context): Boolean {
    var reduce by remember(context) { mutableStateOf(systemReducesMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduce = systemReducesMotion(context) }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduce = systemReducesMotion(context)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

@Composable
fun YousongmovileTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = rememberSystemReducesMotion(context)
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

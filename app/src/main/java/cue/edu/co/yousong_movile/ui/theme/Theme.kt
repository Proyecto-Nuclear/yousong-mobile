package cue.edu.co.yousong_movile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CustomerColors = darkColorScheme(
    primary = Color(0xFFCBDDB0),
    onPrimary = Color(0xFF172012),
    secondary = Color(0xFFB9CBD8),
    background = Color(0xFF101110),
    surface = Color(0xFF171917),
    surfaceVariant = Color(0xFF2A2E29),
    onBackground = Color(0xFFF5F6F1),
    onSurface = Color(0xFFF5F6F1),
    error = Color(0xFFFFB4AB)
)

@Composable
fun YousongmovileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CustomerColors, typography = Typography, content = content)
}

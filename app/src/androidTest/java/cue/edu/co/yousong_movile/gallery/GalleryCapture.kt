package cue.edu.co.yousong_movile.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** A device-independent frame: the density is chosen so [widthDp] always fits the emulator width. */
internal data class Frame(val name: String, val widthDp: Int, val heightDp: Int, val fontScale: Float = 1f)

internal val PhoneFrames = listOf(
    Frame("phone", 360, 760),
    Frame("phone-font200", 360, 760, 2f),
)
internal val SmallFrame = Frame("small", 360, 640)
internal val TabletFrame = Frame("tablet", 840, 600)

/**
 * Renders design-gallery states into PNGs under the app's external files dir
 * (`Android/data/<package>/files/gallery`). Debug/test only; never shipped.
 */
internal class Gallery(private val rule: ComposeContentTestRule, private val folder: String) {
    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var frame by mutableStateOf(PhoneFrames.first())
    private var started = false

    private val output: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir("gallery"), folder).apply { mkdirs() }
    }

    fun shot(name: String, frames: List<Frame> = PhoneFrames, body: @Composable () -> Unit) {
        frames.forEach { target ->
            frame = target
            content = body
            if (!started) {
                started = true
                rule.setContent { Host() }
                // Infinite animations (the equalizer) must not keep the rule busy forever.
                rule.mainClock.autoAdvance = false
            }
            repeat(3) {
                rule.mainClock.advanceTimeBy(600)
                Thread.sleep(150) // let off-main work (fake cover loads) land
            }
            rule.mainClock.advanceTimeBy(600)
            val bitmap = rule.onNodeWithTag(HOST_TAG).captureToImage().asAndroidBitmap()
            File(output, "$name-${target.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Composable
    private fun Host() {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val target = frame
        val density = Density(metrics.widthPixels.toFloat() / target.widthDp, target.fontScale)
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.size(target.widthDp.dp, target.heightDp.dp).testTag(HOST_TAG)) {
                content?.invoke()
            }
        }
    }

    private companion object { const val HOST_TAG = "gallery-host" }
}

package cue.edu.co.yousong_movile

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import cue.edu.co.yousong_movile.ui.theme.LocalReduceMotion
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test

class CoverAndMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coverFollowsTheUrlAndNeverKeepsThePreviousSongsImage() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
        // "fake://a" loads at once; "fake://slow" never finishes loading.
        val loader: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap? = { url ->
            if (url == "fake://a") bitmap else awaitCancellation()
        }
        var url by mutableStateOf<String?>("fake://a")
        compose.setContent {
            YousongmovileTheme {
                CompositionLocalProvider(LocalCoverLoader provides loader) { Cover(url, "Luna azul", Modifier.size(56.dp)) }
            }
        }
        compose.onNodeWithTag(COVER_IMAGE_TAG, useUnmergedTree = true).assertExists()
        url = "fake://slow"
        compose.waitForIdle()
        compose.onNodeWithTag(COVER_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(COVER_PLACEHOLDER_TAG, useUnmergedTree = true).assertExists()
        url = "fake://a"
        compose.waitForIdle()
        compose.onNodeWithTag(COVER_IMAGE_TAG, useUnmergedTree = true).assertExists()
        url = null
        compose.waitForIdle()
        compose.onNodeWithTag(COVER_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(COVER_PLACEHOLDER_TAG, useUnmergedTree = true).assertExists()
    }

    @Test fun reduceMotionFollowsTheSystemSettingLive() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun scale(value: String) = automation.executeShellCommand("settings put global animator_duration_scale $value").close()
        scale("1")
        try {
            compose.setContent { YousongmovileTheme { Text("reduce=${LocalReduceMotion.current}") } }
            compose.onNodeWithText("reduce=false").assertExists()
            scale("0")
            compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("reduce=true") == 1 }
            scale("1")
            compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("reduce=false") == 1 }
        } finally { scale("1") }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}

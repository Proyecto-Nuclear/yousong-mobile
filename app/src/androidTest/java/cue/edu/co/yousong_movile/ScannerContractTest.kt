package cue.edu.co.yousong_movile

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.client.android.Intents
import org.junit.Assert.*
import org.junit.Test

class ScannerContractTest {
    @Test fun actualIntentUsesQrOnlyAndPrivatePortraitActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = qrScannerOptions().createScanIntent(context)
        assertEquals(ComponentName(context, QrCaptureActivity::class.java), intent.component)
        assertEquals("QR_CODE", intent.getStringExtra(Intents.Scan.FORMATS))
        assertFalse(intent.getBooleanExtra(Intents.Scan.BEEP_ENABLED, true))
        assertFalse(intent.getBooleanExtra(Intents.Scan.BARCODE_IMAGE_ENABLED, true))
        assertFalse(intent.getBooleanExtra(Intents.Scan.ORIENTATION_LOCKED, true))
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(requireNotNull(intent.component), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
        assertFalse(info.exported)
    }
}

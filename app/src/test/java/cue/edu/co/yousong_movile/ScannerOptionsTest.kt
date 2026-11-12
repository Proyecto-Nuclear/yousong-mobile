package cue.edu.co.yousong_movile

import com.google.zxing.client.android.Intents
import org.junit.Assert.*
import org.junit.Test

class ScannerOptionsTest {
    @Test fun usesPrivatePortraitCaptureWithoutSensorOrientationLock() {
        val options = qrScannerOptions()
        assertEquals("cue.edu.co.yousong_movile.QrCaptureActivity", options.captureActivity.name)
        assertEquals(false, options.moreExtras[Intents.Scan.ORIENTATION_LOCKED])
    }

    @Test fun preservesSilentScanningWithoutSavingImages() {
        val extras = qrScannerOptions().moreExtras
        assertEquals(false, extras[Intents.Scan.BEEP_ENABLED])
        assertEquals(false, extras[Intents.Scan.BARCODE_IMAGE_ENABLED])
        assertEquals("Escanea el QR del establecimiento", extras[Intents.Scan.PROMPT_MESSAGE])
    }
}

package cue.edu.co.yousong_movile

import android.view.View
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** CaptureManager retains permission, pause/resume and camera cleanup ownership. */
class QrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.customer_qr_capture)
        findViewById<View>(R.id.qr_cancel).setOnClickListener { finish() }
        return findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner).apply {
            val metrics = resources.displayMetrics
            layoutParams = layoutParams.apply {
                width = minOf(metrics.widthPixels - (48 * metrics.density).toInt(), (360 * metrics.density).toInt())
            }
        }
    }
}

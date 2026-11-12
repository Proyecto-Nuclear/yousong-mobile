package cue.edu.co.yousong_movile

import com.journeyapps.barcodescanner.ScanOptions

internal fun qrScannerOptions(): ScanOptions = ScanOptions()
    .setCaptureActivity(QrCaptureActivity::class.java)
    .setOrientationLocked(false)
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("Escanea el QR del establecimiento")
    .setBeepEnabled(false)
    .setBarcodeImageEnabled(false)

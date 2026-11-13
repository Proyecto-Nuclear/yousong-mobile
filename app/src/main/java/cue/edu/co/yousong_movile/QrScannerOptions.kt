package cue.edu.co.yousong_movile

import com.journeyapps.barcodescanner.ScanOptions

/** Callers pass `R.string.scanner_prompt`; the default only keeps the no-argument contract. */
internal const val DEFAULT_SCANNER_PROMPT = "Apunta al QR del local"

internal fun qrScannerOptions(prompt: String = DEFAULT_SCANNER_PROMPT): ScanOptions = ScanOptions()
    .setCaptureActivity(QrCaptureActivity::class.java)
    .setOrientationLocked(false)
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt(prompt)
    .setBeepEnabled(false)
    .setBarcodeImageEnabled(false)

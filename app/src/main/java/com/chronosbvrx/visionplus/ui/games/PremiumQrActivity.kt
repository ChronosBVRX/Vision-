package com.chronosbvrx.visionplus.ui.games

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import com.chronosbvrx.visionplus.R
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Escáner QR con la piel de Vision+ sobre el motor probado de ZXing
 * (orientación, foco y permisos los resuelve la librería). El visor propio
 * dibuja la banda horizontal con esquinas azules y línea animada.
 */
class PremiumQrActivity : Activity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var capture: CaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.party_qr_premium)
        barcodeView = findViewById(R.id.zxing_barcode_scanner)
        barcodeView.initializeFromIntent(intent)
        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}

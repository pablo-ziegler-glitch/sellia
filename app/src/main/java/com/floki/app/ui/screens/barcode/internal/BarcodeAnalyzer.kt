package com.floki.app.ui.screens.barcode.internal

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analizador de frames para ML Kit.
 * - Soporta EAN_13, EAN_8, CODE_128 y QR_CODE (podés ajustar).
 * - Llama a [onBarcode] con el primer valor legible del frame.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // Tomamos el primero con "rawValue"
                val raw = barcodes.firstOrNull { it.rawValue?.isNotBlank() == true }?.rawValue
                if (!raw.isNullOrBlank()) {
                    onBarcode(raw)
                }
            }
            .addOnFailureListener {
                // Silencioso, podrías loguear si querés
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

package com.example.selliaapp.ui.screens.manage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import android.widget.Toast
import com.example.selliaapp.data.local.entity.ProductEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("NewApi")
internal fun exportQrPdf(
    context: Context,
    items: List<ProductEntity>,
    fileName: String,
    includePrices: Boolean,
    currencyFormatter: NumberFormat,
    resolveQrValue: (ProductEntity) -> String,
    resolveSkuValue: (ProductEntity) -> String
) {
    if (items.isEmpty()) {
        Toast.makeText(context, "No hay productos para exportar.", Toast.LENGTH_SHORT).show()
        return
    }
    val labelWidthPoints = mmToPoints(30f)
    val labelHeightPoints = mmToPoints(15f)
    val qrRightMargin = mmToPoints(0.6f)
    val qrVerticalMargin = mmToPoints(1.0f)
    val qrTextGap = mmToPoints(0.8f)
    val qrSize = minOf(mmToPoints(11f), labelHeightPoints - (qrVerticalMargin * 2))
    val qrLeft = labelWidthPoints - qrRightMargin - qrSize
    val textBlockWidth = (qrLeft - qrTextGap).coerceAtLeast(mmToPoints(11f))
    val padding = mmToPoints(0.8f)
    val skuTextSize = mmToPoints(2.8f).toFloat()
    val priceTextSize = mmToPoints(2.0f).toFloat()

    val skuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = skuTextSize
        isFakeBoldText = true
    }
    val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = priceTextSize
    }

    val document = PdfDocument()
    val exportableItems = items.mapNotNull { product ->
        runCatching {
            val qrValue = resolveQrValue(product)
            if (qrValue.isBlank()) {
                error("QR vacío para ${resolveSkuValue(product)}")
            }
            product to generateQrBitmap(qrValue, qrSize)
        }.getOrNull()
    }

    if (exportableItems.isEmpty()) {
        document.close()
        Toast.makeText(
            context,
            "No se pudieron generar QRs válidos para exportar.",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    exportableItems.forEachIndexed { index, (product, qrBitmap) ->
        val pageInfo = PdfDocument.PageInfo.Builder(labelWidthPoints, labelHeightPoints, index + 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val skuValue = resolveSkuValue(product)

        val textMaxWidth = (textBlockWidth - (padding * 2)).toFloat()
        val textLines = mutableListOf(
            ellipsizeToWidth(skuValue, skuPaint, textMaxWidth)
        )
        if (includePrices) {
            textLines += buildPriceLine(
                fullLabel = "Lista",
                shortLabel = "Lista",
                value = product.listPrice,
                currencyFormatter = currencyFormatter,
                paint = pricePaint,
                maxWidthPx = textMaxWidth
            )
            textLines += buildPriceLine(
                fullLabel = "Efectivo",
                shortLabel = "Efect",
                value = product.cashPrice ?: product.listPrice,
                currencyFormatter = currencyFormatter,
                paint = pricePaint,
                maxWidthPx = textMaxWidth
            )
        }

        val spacing = mmToPoints(0.6f).toFloat()
        val lineHeights = textLines.mapIndexed { lineIndex, _ ->
            val paint = if (lineIndex == 0) skuPaint else pricePaint
            paint.fontMetrics.run { descent - ascent }
        }
        val totalTextHeight = lineHeights.sum() + spacing * (textLines.size - 1)
        var currentTop = (labelHeightPoints - totalTextHeight) / 2f

        textLines.forEachIndexed { lineIndex, line ->
            val paint = if (lineIndex == 0) skuPaint else pricePaint
            val textBounds = Rect().also { paint.getTextBounds(line, 0, line.length, it) }
            val textX = textBlockWidth / 2f - textBounds.exactCenterX()
            val baseline = currentTop - paint.fontMetrics.ascent
            canvas.drawText(line, textX, baseline, paint)
            currentTop += lineHeights[lineIndex] + spacing
        }

        val qrTop = (labelHeightPoints - qrSize) / 2
        canvas.drawBitmap(
            qrBitmap,
            null,
            Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
            null
        )
        document.finishPage(page)
    }

    val resolver = context.contentResolver
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val safeName = fileName.ifBlank { "qr_${timestamp}" }
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$safeName.pdf")
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Sellia")
    }
    runCatching {
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo")
        val outputStream: OutputStream = resolver.openOutputStream(uri)
            ?: error("No se pudo escribir el archivo")
        outputStream.use { document.writeTo(it) }
    }.onSuccess {
        val skippedCount = items.size - exportableItems.size
        val message = if (skippedCount > 0) {
            "PDF guardado en Descargas/Sellia. Omitidos: $skippedCount QR inválidos."
        } else {
            "PDF guardado en Descargas/Sellia"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(
            context,
            "No se pudo exportar el PDF. Revisá el contenido de los códigos QR.",
            Toast.LENGTH_LONG
        ).show()
    }

    document.close()
}

internal fun buildPriceLine(
    fullLabel: String,
    shortLabel: String,
    value: Double?,
    currencyFormatter: NumberFormat,
    paint: Paint,
    maxWidthPx: Float
): String {
    val valueText = formatPrice(value, currencyFormatter)
    val prioritizedWithShortLabel = "$shortLabel $valueText"

    if (paint.measureText(prioritizedWithShortLabel) > maxWidthPx) {
        return ellipsizeToWidth(valueText, paint, maxWidthPx)
    }

    val fullCandidate = "$fullLabel $valueText"
    return if (paint.measureText(fullCandidate) <= maxWidthPx) {
        fullCandidate
    } else {
        prioritizedWithShortLabel
    }
}

internal fun formatPrice(value: Double?, currencyFormatter: NumberFormat): String {
    val safeValue = value ?: return "-"
    return currencyFormatter.format(safeValue)
}

internal fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    if (content.isBlank()) {
        throw WriterException("QR content is blank")
    }
    val writer = QRCodeWriter()
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

internal fun resolveSkuValue(product: ProductEntity): String {
    return product.code?.takeIf { it.isNotBlank() }
        ?: product.barcode?.takeIf { it.isNotBlank() }
        ?: "SKU-${product.id}"
}

internal fun mmToPoints(mm: Float): Int {
    return (mm * 72f / 25.4f).roundToInt()
}

internal fun ellipsizeToWidth(text: String, paint: Paint, maxWidthPx: Float): String {
    if (text.isBlank()) return text
    if (paint.measureText(text) <= maxWidthPx) return text
    val ellipsis = "…"
    var candidate = text
    while (candidate.isNotEmpty() && paint.measureText(candidate + ellipsis) > maxWidthPx) {
        candidate = candidate.dropLast(1)
    }
    return if (candidate.isEmpty()) ellipsis else candidate + ellipsis
}

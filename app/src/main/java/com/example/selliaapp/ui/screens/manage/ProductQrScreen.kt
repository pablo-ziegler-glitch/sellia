package com.example.selliaapp.ui.screens.manage

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.MarketingSettings
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.ProductViewModel
import com.example.selliaapp.viewmodel.MarketingConfigViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private enum class QrAudience { PUBLIC, OWNER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductQrScreen(
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel(),
    marketingVm: MarketingConfigViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsStateWithLifecycle(initialValue = emptyList())
    val marketingSettings by marketingVm.settings.collectAsStateWithLifecycle(
        initialValue = MarketingSettings()
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var qrAudience by remember { mutableStateOf(QrAudience.PUBLIC) }
    var previewProduct by remember { mutableStateOf<ProductEntity?>(null) }

    fun resolveQrValue(product: ProductEntity): String {
        val queryValue = resolveSkuValue(product)
        val baseUrl = marketingSettings.publicStoreUrl.trim().trimEnd('/')

        if (baseUrl.isNotBlank()) {
            val encoded = URLEncoder.encode(queryValue, StandardCharsets.UTF_8.name())
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val mode = if (qrAudience == QrAudience.OWNER) "owner" else "public"
            return "$baseUrl$separator" + "q=$encoded&mode=$mode"
        }

        return if (qrAudience == QrAudience.OWNER) {
            "sellia://product?q=" + URLEncoder.encode(queryValue, StandardCharsets.UTF_8.name())
        } else {
            queryValue
        }
    }

    @SuppressLint("NewApi")
    fun exportQrPdf(items: List<ProductEntity>, fileName: String) {
        if (items.isEmpty()) {
            Toast.makeText(context, "No hay productos para exportar.", Toast.LENGTH_SHORT).show()
            return
        }
        val labelWidthPoints = mmToPoints(30f)
        val labelHeightPoints = mmToPoints(15f)
        val qrBlockWidth = labelWidthPoints / 2
        val textBlockWidth = labelWidthPoints - qrBlockWidth
        val qrSize = minOf(labelHeightPoints, qrBlockWidth)
        val padding = mmToPoints(0.8f)
        val skuTextSize = mmToPoints(2.7f).toFloat()

        val skuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = skuTextSize
            isFakeBoldText = true
        }

        val document = PdfDocument()
        items.forEachIndexed { index, product ->
            val pageInfo = PdfDocument.PageInfo.Builder(labelWidthPoints, labelHeightPoints, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val skuValue = resolveSkuValue(product)

            val skuText = ellipsizeToWidth(skuValue, skuPaint, (textBlockWidth - (padding * 2)).toFloat())
            val textBounds = Rect().also { skuPaint.getTextBounds(skuText, 0, skuText.length, it) }
            val skuX = textBlockWidth / 2f - textBounds.exactCenterX()
            val skuY = labelHeightPoints / 2f - textBounds.exactCenterY()
            canvas.drawText(skuText, skuX, skuY, skuPaint)

            val qrBitmap = generateQrBitmap(resolveQrValue(product), qrSize)
            val qrLeft = textBlockWidth + ((qrBlockWidth - qrSize) / 2)
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
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$safeName.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Sellia")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            document.close()
            Toast.makeText(context, "No se pudo crear el archivo.", Toast.LENGTH_SHORT).show()
            return
        }
        val outputStream: OutputStream? = resolver.openOutputStream(uri)
        if (outputStream == null) {
            document.close()
            Toast.makeText(context, "No se pudo escribir el archivo.", Toast.LENGTH_SHORT).show()
            return
        }
        outputStream.use { document.writeTo(it) }
        document.close()
        Toast.makeText(context, "PDF guardado en Descargas/Sellia", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = "Códigos QR",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch { exportQrPdf(products, "qr_todos") }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar todos")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Formato de descarga: etiqueta 30mm x 15mm (SKU centrado en su bloque y QR centrado al máximo tamaño posible)."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = qrAudience == QrAudience.PUBLIC,
                    onClick = { qrAudience = QrAudience.PUBLIC },
                    label = { Text("QR público") }
                )
                FilterChip(
                    selected = qrAudience == QrAudience.OWNER,
                    onClick = { qrAudience = QrAudience.OWNER },
                    label = { Text("QR interno") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seleccionados: ${selectedIds.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedIds = products.map { it.id }.toSet() }) {
                        Text("Seleccionar todo")
                    }
                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text("Limpiar")
                    }
                }
            }
            Button(
                onClick = {
                    val selected = products.filter { selectedIds.contains(it.id) }
                    scope.launch { exportQrPdf(selected, "qr_seleccionados") }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descargar seleccionados")
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(product.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + product.id
                                    } else {
                                        selectedIds - product.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(resolveSkuValue(product))
                                Text(resolveQrValue(product))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { previewProduct = product }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Ver QR")
                            }
                            IconButton(onClick = {
                                scope.launch { exportQrPdf(listOf(product), "qr_${product.id}") }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Descargar")
                            }
                        }
                    }
                }
            }
        }
    }

    previewProduct?.let { product ->
        val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 220.dp.roundToPx() }
        val bitmap = remember(product) { generateQrBitmap(resolveQrValue(product), sizePx) }
        AlertDialog(
            onDismissRequest = { previewProduct = null },
            title = { Text(resolveSkuValue(product)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR del producto",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(resolveQrValue(product))
                }
            },
            confirmButton = {
                TextButton(onClick = { previewProduct = null }) { Text("Cerrar") }
            }
        )
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

private fun resolveSkuValue(product: ProductEntity): String {
    return product.code?.takeIf { it.isNotBlank() }
        ?: product.barcode?.takeIf { it.isNotBlank() }
        ?: "SKU-${product.id}"
}


private fun mmToPoints(mm: Float): Int {
    return (mm * 72f / 25.4f).roundToInt()
}

private fun ellipsizeToWidth(text: String, paint: Paint, maxWidthPx: Float): String {
    if (text.isBlank()) return text
    if (paint.measureText(text) <= maxWidthPx) return text
    val ellipsis = "…"
    var candidate = text
    while (candidate.isNotEmpty() && paint.measureText(candidate + ellipsis) > maxWidthPx) {
        candidate = candidate.dropLast(1)
    }
    return if (candidate.isEmpty()) ellipsis else candidate + ellipsis
}

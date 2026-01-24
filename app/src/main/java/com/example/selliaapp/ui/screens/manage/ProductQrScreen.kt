package com.example.selliaapp.ui.screens.manage

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
 import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
 import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selliaapp.data.local.entity.ProductEntity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductQrScreen(
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel(),
    marketingVm: MarketingConfigViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsStateWithLifecycle(initialValue = emptyList())
    val marketingSettings by marketingVm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var sizeCmInput by remember { mutableStateOf("5") }
    var previewProduct by remember { mutableStateOf<ProductEntity?>(null) }

    fun resolveQrValue(product: ProductEntity): String {
        val baseUrl = marketingSettings.publicStoreUrl.trim()
        if (baseUrl.isNotBlank()) {
            val queryValue = product.code?.takeIf { it.isNotBlank() }
                ?: product.barcode?.takeIf { it.isNotBlank() }
                ?: "PRODUCT-${product.id}"
            val encoded = URLEncoder.encode(queryValue, StandardCharsets.UTF_8.name())
            val separator = if (baseUrl.contains("?")) "&" else "?"
            return "${baseUrl.trimEnd('/')}$separator" + "q=$encoded"
        }
        return product.code?.takeIf { it.isNotBlank() }
            ?: product.barcode?.takeIf { it.isNotBlank() }
            ?: "PRODUCT-${product.id}"
    }

    fun parseSizeCm(): Float {
        return sizeCmInput.replace(',', '.').toFloatOrNull()?.coerceIn(1f, 20f) ?: 5f
    }

    @SuppressLint("NewApi")
    fun exportQrPdf(items: List<ProductEntity>, fileName: String) {
        if (items.isEmpty()) {
            Toast.makeText(context, "No hay productos para exportar.", Toast.LENGTH_SHORT).show()
            return
        }
        val sizeCm = parseSizeCm()
        val pageSize = cmToPoints(sizeCm)
        val document = PdfDocument()
        items.forEachIndexed { index, product ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize, pageSize, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val qrSize = (pageSize * 0.9f).roundToInt().coerceAtLeast(64)
            val qrBitmap = generateQrBitmap(resolveQrValue(product), qrSize)
            val left = ((pageSize - qrSize) / 2f).roundToInt()
            val top = ((pageSize - qrSize) / 2f).roundToInt()
            canvas.drawBitmap(qrBitmap, left.toFloat(), top.toFloat(), null)
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
            OutlinedTextField(
                value = sizeCmInput,
                onValueChange = { sizeCmInput = it },
                label = { Text("Tamaño del QR (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
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
                                Text(product.name)
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
            title = { Text(product.name) },
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

private fun cmToPoints(cm: Float): Int {
    return (cm * 72f / 2.54f).roundToInt()
}

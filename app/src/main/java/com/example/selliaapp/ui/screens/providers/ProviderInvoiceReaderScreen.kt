package com.example.selliaapp.ui.screens.providers

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.providers.ProviderInvoiceReaderViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderInvoiceReaderScreen(
    onBack: () -> Unit,
    vm: ProviderInvoiceReaderViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val context = LocalContext.current
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val scope = rememberCoroutineScope()

    fun processBitmap(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val detectedText = result.text.trim()
                if (detectedText.isNotBlank()) {
                    val merged = if (state.rawText.isBlank()) detectedText else "${state.rawText}\n\n$detectedText"
                    vm.onRawTextChange(merged)
                }
            }
            .addOnFailureListener {
                vm.onError("No se pudo leer texto de la imagen. Intentá con otra foto más nítida.")
            }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) processBitmap(bitmap)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.onSuccess(::processBitmap)
                .onFailure {
                    vm.onError("No se pudo abrir la imagen seleccionada")
                }
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(title = "Lectura de facturas", onBack = onBack) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Pegá el texto OCR de la factura o sacá/elegí una foto para detectar automáticamente ítems y totales.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { takePictureLauncher.launch(null) }) {
                        Text("Sacar foto")
                    }
                    TextButton(onClick = { pickImageLauncher.launch("image/*") }) {
                        Text("Elegir imagen")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.rawText,
                    onValueChange = vm::onRawTextChange,
                    label = { Text("Texto de factura") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextButton(onClick = vm::analyze) {
                    Text("Analizar factura")
                }
            }

            state.errorMessage?.let { message ->
                item { Text(message) }
            }

            state.parsed?.let { draft ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Factura: ${draft.invoiceNumber ?: "No detectada"}")
                            Text("Proveedor: ${draft.providerName ?: "No detectado"}")
                            Text("CUIT/RUT: ${draft.providerTaxId ?: "No detectado"}")
                            Text("Fecha: ${draft.issueDateMillis?.let { sdf.format(Date(it)) } ?: "No detectada"}")
                            Text("Total: ${draft.totalAmount?.let { "%.2f".format(it) } ?: "No detectado"} ${draft.currencySymbol}")
                        }
                    }
                }

                if (draft.warnings.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Observaciones")
                            draft.warnings.forEach { warning ->
                                AssistChip(onClick = {}, label = { Text(warning) })
                            }
                        }
                    }
                }

                item { Text("Productos detectados (${draft.items.size})") }
                items(draft.items) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${item.code ?: "-"} • ${item.name}")
                            Text("Cant: ${item.quantity} | Unit: ${"%.2f".format(item.unitPrice)} | Total: ${"%.2f".format(item.lineTotal)}")
                            item.vatPercent?.let { Text("IVA: ${"%.2f".format(it)}%") }
                        }
                    }
                }
            }
        }
    }
}

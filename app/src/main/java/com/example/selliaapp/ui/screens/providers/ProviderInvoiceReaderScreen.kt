package com.example.selliaapp.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selliaapp.ui.components.BackTopAppBar
import com.example.selliaapp.viewmodel.providers.ProviderInvoiceReaderViewModel
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
                Text("Pegá el texto OCR de la factura (Google Lens/Drive/PDF) y analizamos automáticamente ítems y totales.")
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

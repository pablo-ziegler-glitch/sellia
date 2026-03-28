package com.example.selliaapp.ui.screens.providers


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderInvoiceDetailScreen(
    invoiceId: Int,
    repo: ProviderInvoiceRepository,
    onBack: () -> Unit
) {
    val row by repo.observeDetail(invoiceId).collectAsState(initial = null)
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val receivingByItem = remember { mutableStateMapOf<Int, String>() }
    var discrepancyNote by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopAppBar(title = "Detalle de Factura", onBack = onBack) }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            row?.let { data ->
                val inv = data.invoice
                Text("Factura: ${inv.number}")
                Text("Fecha: ${sdf.format(Date(inv.issueDateMillis))}")
                Text("Estado: ${inv.status}")
                Text("Recepción: ${inv.receptionStatus}")
                if (inv.paymentRef != null) Text("Pago ref: ${inv.paymentRef}")
                if (inv.paymentAmount != null) Text("Monto pagado: ${"%.2f".format(inv.paymentAmount)}")
                if (!inv.discrepancyNote.isNullOrBlank()) Text("Nota de discrepancia:\n${inv.discrepancyNote}")

                HorizontalDivider()

                Text("Items (pedido vs recibido)")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(data.items) { it ->
                        val receivedInput = receivingByItem[it.id] ?: it.receivedQuantity?.toString().orEmpty()
                        ListItem(
                            headlineContent = {
                                Text("${it.code ?: "-"}  ${it.name}")
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Pedido: ${it.quantity}  •  Recibido actual: ${it.receivedQuantity ?: 0.0}")
                                    Text("P.Unit: ${"%.2f".format(it.priceUnit)}  •  Total: ${"%.2f".format(it.total)}")
                                    OutlinedTextField(
                                        value = receivedInput,
                                        onValueChange = { v -> receivingByItem[it.id] = v },
                                        label = { Text("Recibido real") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = discrepancyNote,
                    onValueChange = { discrepancyNote = it },
                    label = { Text("Nota de discrepancia (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                saveError?.let { Text(it) }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val resolved = data.items.associate { item ->
                                        val value = (receivingByItem[item.id] ?: item.receivedQuantity?.toString().orEmpty())
                                            .replace(',', '.')
                                            .toDoubleOrNull()
                                            ?: 0.0
                                        item.id to value
                                    }
                                    repo.confirmReception(
                                        invoiceId = inv.id,
                                        receivedByItemId = resolved,
                                        discrepancyNote = discrepancyNote
                                    )
                                }.onSuccess {
                                    saveError = null
                                }.onFailure { error ->
                                    saveError = error.message ?: "No se pudo confirmar la recepción."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar recepción y actualizar stock")
                    }
                }
            } ?: Text("Cargando...")
        }
    }
}

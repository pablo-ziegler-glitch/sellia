package com.example.selliaapp.ui.screens.providers



import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.selliaapp.repository.ProviderInvoiceRepository
import com.example.selliaapp.ui.components.BackTopAppBar
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPaymentsScreen(
    repo: ProviderInvoiceRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pending by repo.observePending().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }

    var ref by remember { mutableStateOf(TextFieldValue("")) }
    var amount by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(topBar = { BackTopAppBar(title = "Pagos a Proveedores", onBack = onBack) }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending) { row ->
                    val inv = row.invoice
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Factura ${inv.number}  • Total: ${"%.2f".format(inv.total)}") },
                            supportingContent = { Text("Proveedor #${inv.providerId}  • Fecha: ${Date(inv.issueDateMillis)}") },
                            trailingContent = {
                                Button(onClick = { selectedId = inv.id; showDialog = true }) {
                                    Text("Marcar Paga")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog && selectedId != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Referencia/ID de pago") })
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto pagado") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = selectedId!!
                    val row = pending.firstOrNull { it.invoice.id == id } ?: return@TextButton
                    val amt = amount.text.toDoubleOrNull() ?: 0.0
                    scope.launch {
                        repo.markPaid(
                            invoice = row.invoice,
                            ref = ref.text.trim(),
                            amount = amt,
                            paymentDateMillis = System.currentTimeMillis()
                        )
                        showDialog = false
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}
